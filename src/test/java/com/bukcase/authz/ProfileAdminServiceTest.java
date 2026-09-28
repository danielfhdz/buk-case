package com.bukcase.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bukcase.IntegrationTest;
import com.bukcase.authz.admin.AdminRuleViolation;
import com.bukcase.authz.admin.ProfileAdminService;
import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.ResourceCatalog;
import com.bukcase.identity.CurrentUser;
import com.bukcase.modules.assets.AssetService;
import com.bukcase.modules.vacations.VacationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Administration rules and cache invalidation. Changes are committed for real (invalidation only
 * happens after commit) and removed afterwards; each test works on a throwaway user.
 */
@IntegrationTest
class ProfileAdminServiceTest {

    private static final long LAURA_ONLY_ADMIN = 1;
    private static final long ADMIN_PROFILE = 1;
    private static final long VACATIONS_NORTH_PROFILE = 3;
    private static final long ROOT_AREA = 1;
    private static final long NORTH_SALES = 3;
    private static final long SOUTH_SALES = 4;

    @Autowired
    ProfileAdminService admin;

    @Autowired
    AssetService assets;

    @Autowired
    VacationService vacations;

    @Autowired
    ResourceCatalog resources;

    @Autowired
    JdbcTemplate jdbc;

    private long tempUser;
    private final List<Long> createdProfiles = new ArrayList<>();

    @BeforeEach
    void createTempUser() {
        tempUser = jdbc.queryForObject(
                "INSERT INTO users (username) VALUES ('test.' || gen_random_uuid()) RETURNING id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_profiles WHERE user_id = ?", tempUser);
        jdbc.update("DELETE FROM users WHERE id = ?", tempUser);
        createdProfiles.forEach(admin::deleteProfile);
    }

    @Test
    void lastAdministratorCannotBeRemoved() {
        assertThatThrownBy(() -> admin.unassign(LAURA_ONLY_ADMIN, ADMIN_PROFILE))
                .isInstanceOf(AdminRuleViolation.class);
    }

    @Test
    void administratorCanBeRemovedWhenAnotherOneRemains() {
        admin.assign(tempUser, ADMIN_PROFILE);

        admin.unassign(tempUser, ADMIN_PROFILE);

        assertThat(profilesOf(tempUser)).isEmpty();
    }

    @Test
    void systemProfileCannotBeEdited() {
        assertThatThrownBy(() -> admin.grant(ADMIN_PROFILE, ROOT_AREA, resources.idOf("ASSETS"), AccessLevel.READ))
                .isInstanceOf(AdminRuleViolation.class);
        assertThatThrownBy(() -> admin.deleteProfile(ADMIN_PROFILE))
                .isInstanceOf(AdminRuleViolation.class);
    }

    @Test
    void grantIsEnforcedRightAfterItIsCommitted() {
        assertThat(CurrentUser.runAs(tempUser, () -> assets.list())).isEmpty();

        long profile = newProfile("Test - assets reader");
        admin.grant(profile, ROOT_AREA, resources.idOf("ASSETS"), AccessLevel.READ);
        admin.assign(tempUser, profile);

        assertThat(CurrentUser.runAs(tempUser, () -> assets.list())).hasSize(7);

        admin.revoke(profile, ROOT_AREA, resources.idOf("ASSETS"));

        assertThat(CurrentUser.runAs(tempUser, () -> assets.list())).isEmpty();
    }

    @Test
    void clonedProfileAppliesTheSameGrantsToAnotherArea() {
        long southCopy = admin.cloneProfile(VACATIONS_NORTH_PROFILE, uniqueName("Test - vacations south"),
                Map.of(NORTH_SALES, SOUTH_SALES));
        createdProfiles.add(southCopy);
        admin.assign(tempUser, southCopy);

        List<String> employees = CurrentUser.runAs(tempUser, () -> vacations.list()).stream()
                .map(request -> request.getEmployee().getFullName())
                .toList();

        assertThat(employees).containsExactly("Sofia South");
    }

    @Test
    void permissionsOfSeveralProfilesAreCombined() {
        long assetsProfile = newProfile("Test - phones");
        admin.grant(assetsProfile, ROOT_AREA, resources.idOf("ASSETS.PHONES"), AccessLevel.READ);
        long vehiclesProfile = newProfile("Test - vehicles");
        admin.grant(vehiclesProfile, ROOT_AREA, resources.idOf("ASSETS.VEHICLES"), AccessLevel.READ);
        admin.assign(tempUser, assetsProfile);
        admin.assign(tempUser, vehiclesProfile);

        List<String> categories = CurrentUser.runAs(tempUser, () -> assets.list()).stream()
                .map(asset -> asset.getCategory().getName())
                .distinct()
                .toList();

        assertThat(categories).containsExactlyInAnyOrder("Phones", "Vehicles");
    }

    private long newProfile(String name) {
        long id = admin.createProfile(uniqueName(name));
        createdProfiles.add(id);
        return id;
    }

    private static String uniqueName(String name) {
        return name + " " + UUID.randomUUID();
    }

    private List<Long> profilesOf(long userId) {
        return jdbc.queryForList("SELECT profile_id FROM user_profiles WHERE user_id = ?", Long.class, userId);
    }
}
