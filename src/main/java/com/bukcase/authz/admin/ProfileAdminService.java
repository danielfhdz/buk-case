package com.bukcase.authz.admin;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.cache.AuthzVersion;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Write side of the authorization configuration. Every mutation invalidates cached permissions by
 * bumping the tenant version after the transaction commits: bumping before commit would let a
 * concurrent reader compile the old data under the new version and cache stale permissions.
 */
@Service
public class ProfileAdminService {

    private final JdbcTemplate jdbc;
    private final AuthzVersion version;

    public ProfileAdminService(JdbcTemplate jdbc, AuthzVersion version) {
        this.jdbc = jdbc;
        this.version = version;
    }

    @Transactional
    public long createProfile(String name) {
        Long id = jdbc.queryForObject("INSERT INTO profiles (name) VALUES (?) RETURNING id", Long.class, name);
        invalidateAfterCommit();
        return id;
    }

    @Transactional
    public void grant(long profileId, long areaId, long resourceId, AccessLevel level) {
        requireEditable(profileId);
        jdbc.update("""
                INSERT INTO profile_grants (profile_id, area_id, resource_id, access_level)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (profile_id, area_id, resource_id) DO UPDATE SET access_level = EXCLUDED.access_level
                """, profileId, areaId, resourceId, level.code());
        invalidateAfterCommit();
    }

    @Transactional
    public void revoke(long profileId, long areaId, long resourceId) {
        requireEditable(profileId);
        jdbc.update("DELETE FROM profile_grants WHERE profile_id = ? AND area_id = ? AND resource_id = ?",
                profileId, areaId, resourceId);
        invalidateAfterCommit();
    }

    /**
     * Creates a copy of a profile, replacing areas according to {@code areaMapping}
     * (source area id to target area id). Areas not in the mapping are kept. The copy is
     * independent: later changes to the source are not propagated.
     */
    @Transactional
    public long cloneProfile(long sourceProfileId, String newName, Map<Long, Long> areaMapping) {
        requireEditable(sourceProfileId);
        long cloneId = createProfile(newName);
        jdbc.query("SELECT area_id, resource_id, access_level FROM profile_grants WHERE profile_id = ?", rs -> {
            long sourceArea = rs.getLong("area_id");
            jdbc.update("""
                    INSERT INTO profile_grants (profile_id, area_id, resource_id, access_level)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (profile_id, area_id, resource_id) DO NOTHING
                    """, cloneId, areaMapping.getOrDefault(sourceArea, sourceArea),
                    rs.getLong("resource_id"), rs.getShort("access_level"));
        }, sourceProfileId);
        return cloneId;
    }

    @Transactional
    public void deleteProfile(long profileId) {
        requireEditable(profileId);
        jdbc.update("DELETE FROM profiles WHERE id = ?", profileId);
        invalidateAfterCommit();
    }

    @Transactional
    public void assign(long userId, long profileId) {
        jdbc.update("INSERT INTO user_profiles (user_id, profile_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, profileId);
        invalidateAfterCommit();
    }

    /**
     * Removes a profile from a user. Rejected when it would leave the tenant without any
     * administrator. The admin profile row is locked so two concurrent removals cannot both pass
     * the check.
     */
    @Transactional
    public void unassign(long userId, long profileId) {
        boolean isAdminProfile = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_admin FROM profiles WHERE id = ? FOR UPDATE", Boolean.class, profileId));
        if (isAdminProfile) {
            Integer otherAdmins = jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT up.user_id)
                    FROM user_profiles up JOIN profiles p ON p.id = up.profile_id
                    WHERE p.is_admin AND up.user_id <> ?
                    """, Integer.class, userId);
            if (otherAdmins == null || otherAdmins == 0) {
                throw new AdminRuleViolation("The tenant must keep at least one administrator");
            }
        }
        jdbc.update("DELETE FROM user_profiles WHERE user_id = ? AND profile_id = ?", userId, profileId);
        invalidateAfterCommit();
    }

    private void requireEditable(long profileId) {
        Boolean system = jdbc.queryForObject("SELECT is_system FROM profiles WHERE id = ?", Boolean.class, profileId);
        if (Boolean.TRUE.equals(system)) {
            throw new AdminRuleViolation("System profiles cannot be modified, cloned or deleted");
        }
    }

    private void invalidateAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            version.bump();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                version.bump();
            }
        });
    }
}
