package com.bukcase.authz.engine;

import com.bukcase.authz.api.AccessLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PermissionCompiler {

    private static final String USER_GRANTS = """
            SELECT p.is_admin, g.area_id, g.resource_id, g.access_level
            FROM user_profiles up
            JOIN profiles p ON p.id = up.profile_id
            LEFT JOIN profile_grants g ON g.profile_id = p.id
            WHERE up.user_id = ?
            """;

    private final JdbcTemplate jdbc;

    public PermissionCompiler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CompiledPermissions compile(long userId) {
        boolean[] admin = {false};
        Map<List<Long>, AccessLevel> strongest = new LinkedHashMap<>();
        jdbc.query(USER_GRANTS, rs -> {
            admin[0] |= rs.getBoolean("is_admin");
            long areaId = rs.getLong("area_id");
            if (rs.wasNull()) {
                return;
            }
            AccessLevel level = AccessLevel.fromCode(rs.getShort("access_level"));
            strongest.merge(List.of(areaId, rs.getLong("resource_id")), level,
                    (current, candidate) -> candidate.satisfies(current) ? candidate : current);
        }, userId);

        List<Grant> grants = new ArrayList<>(strongest.size());
        strongest.forEach((cell, level) -> grants.add(new Grant(cell.get(0), cell.get(1), level)));
        return new CompiledPermissions(userId, admin[0], List.copyOf(grants));
    }
}
