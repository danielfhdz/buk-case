package com.bukcase.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bukcase.IntegrationTest;
import com.bukcase.authz.api.AccessDeniedException;
import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import com.bukcase.identity.CurrentUser;
import com.bukcase.identity.NoCurrentUserException;
import com.bukcase.modules.assets.Asset;
import com.bukcase.modules.assets.AssetRepository;
import com.bukcase.modules.assets.AssetService;
import com.bukcase.modules.complaints.Complaint;
import com.bukcase.modules.complaints.ComplaintService;
import com.bukcase.modules.vacations.VacationRequest;
import com.bukcase.modules.vacations.VacationRequestRepository;
import com.bukcase.modules.vacations.VacationService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The scenarios from the business case, run against the demo data set
 * ({@code db/demo/V100__demo_data.sql}). Read-only: no test mutates data.
 */
@IntegrationTest
class AuthorizationScenariosTest {

    private static final long LAURA_ADMIN = 1;
    private static final long ANDRES_COMMERCIAL_MANAGER = 2;
    private static final long CAROLINA_NORTH_VACATIONS = 3;
    private static final long PEDRO_ASSETS = 4;
    private static final long JORGE_IT_MANAGER = 5;
    private static final long ELENA_HARASSMENT_OFFICER = 7;
    private static final long TOMAS_FRAUD_OFFICER = 8;
    private static final long NICOLAS_NO_PROFILE = 9;

    private static final long MACBOOK = 1;
    private static final long TOYOTA_HILUX = 6;
    private static final long SPARE_THINKPAD = 7;

    @Autowired
    AssetService assets;

    @Autowired
    AssetRepository assetRepository;

    @Autowired
    ComplaintService complaints;

    @Autowired
    VacationService vacations;

    @Autowired
    VacationRequestRepository vacationRepository;

    @Autowired
    Authorizer authorizer;

    @Nested
    @DisplayName("Level 1: module and area")
    class ModuleAndArea {

        @Test
        void companyWideGrantSeesEveryAssetIncludingUnassigned() {
            assertThat(assetNamesFor(PEDRO_ASSETS)).containsExactlyInAnyOrder(
                    "MacBook Pro 14", "ThinkPad T14", "Lenovo IdeaPad", "iPhone 15",
                    "Samsung Galaxy S24", "Toyota Hilux", "Spare ThinkPad");
        }

        @Test
        void areaGrantCoversSubAreasOnlyAndExcludesRecordsWithoutArea() {
            assertThat(assetNamesFor(ANDRES_COMMERCIAL_MANAGER)).containsExactlyInAnyOrder(
                    "MacBook Pro 14", "ThinkPad T14", "iPhone 15", "Samsung Galaxy S24");
        }

        @Test
        void areaScopeLimitsVacationRequestsToNorthSales() {
            List<String> employees = CurrentUser.runAs(CAROLINA_NORTH_VACATIONS, () -> vacations.list())
                    .stream().map(request -> request.getEmployee().getFullName()).toList();

            assertThat(employees).containsExactlyInAnyOrder("Carolina North", "Diego North");
        }

        @Test
        void userWithoutProfilesSeesNothing() {
            assertThat(assetNamesFor(NICOLAS_NO_PROFILE)).isEmpty();
        }

        @Test
        void administratorSeesEverything() {
            assertThat(assetNamesFor(LAURA_ADMIN)).hasSize(7);
        }

        @Test
        void readOnlyGrantAllowsReadingButNotWriting() {
            Asset macbook = assetRepository.findById(MACBOOK).orElseThrow();

            CurrentUser.runAs(ANDRES_COMMERCIAL_MANAGER, () -> {
                assertThat(authorizer.can(AccessLevel.READ, macbook)).isTrue();
                assertThat(authorizer.can(AccessLevel.WRITE, macbook)).isFalse();
            });
        }

        @Test
        void writeGrantOnAreaAllowsWritingInsideItOnly() {
            List<VacationRequest> all = vacationRepository.findAll();

            CurrentUser.runAs(CAROLINA_NORTH_VACATIONS, () -> all.forEach(request ->
                    assertThat(authorizer.can(AccessLevel.WRITE, request))
                            .as(request.getEmployee().getFullName())
                            .isEqualTo(request.getEmployee().getFullName().endsWith("North"))));
        }
    }

    @Nested
    @DisplayName("Level 2: entity types")
    class EntityTypes {

        @Test
        void entityGrantsRestrictToComputersAndPhones() {
            assertThat(assetNamesFor(JORGE_IT_MANAGER))
                    .contains("Spare ThinkPad")
                    .doesNotContain("Toyota Hilux")
                    .hasSize(6);
        }

        @Test
        void pointCheckDeniesEntityTypeOutsideGrant() {
            CurrentUser.runAs(JORGE_IT_MANAGER, () -> {
                assertThat(assets.get(SPARE_THINKPAD).getName()).isEqualTo("Spare ThinkPad");
                assertThatThrownBy(() -> assets.get(TOYOTA_HILUX)).isInstanceOf(AccessDeniedException.class);
            });
        }

        @Test
        void complaintOfficersDoNotSeeEachOthersTypes() {
            assertThat(complaintTypesFor(ELENA_HARASSMENT_OFFICER))
                    .containsOnly("Harassment", "Discrimination");
            assertThat(complaintTypesFor(TOMAS_FRAUD_OFFICER))
                    .containsOnly("Fraud");
        }

        @Test
        void moduleGateAcceptsGrantsOnEntitiesBelowTheModule() {
            assertThat(CurrentUser.runAs(JORGE_IT_MANAGER, () -> assets.categories())).isNotEmpty();
        }

        @Test
        void moduleGateRejectsUsersWithoutAnyGrantInTheModule() {
            CurrentUser.runAs(CAROLINA_NORTH_VACATIONS, () ->
                    assertThatThrownBy(() -> assets.categories()).isInstanceOf(AccessDeniedException.class));
        }
    }

    @Nested
    @DisplayName("Execution context")
    class ExecutionContext {

        @Test
        void asyncJobEvaluatesPermissionsOfTheEnqueuingUser() {
            List<Asset> fromJob = CompletableFuture
                    .supplyAsync(() -> CurrentUser.runAs(JORGE_IT_MANAGER, () -> assets.list()))
                    .join();

            assertThat(fromJob).hasSize(6);
        }

        @Test
        void workWithoutBoundUserIsRejected() {
            CompletableFuture<List<Asset>> unbound = CompletableFuture.supplyAsync(() -> assets.list());

            assertThatThrownBy(unbound::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NoCurrentUserException.class);
        }
    }

    private List<String> assetNamesFor(long userId) {
        return CurrentUser.runAs(userId, () -> assets.list()).stream().map(Asset::getName).toList();
    }

    private List<String> complaintTypesFor(long userId) {
        return CurrentUser.runAs(userId, () -> complaints.list()).stream()
                .map(Complaint::getType)
                .map(type -> type.getName())
                .toList();
    }
}
