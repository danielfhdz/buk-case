package com.bukcase.demo;

import com.bukcase.authz.admin.AdminRuleViolation;
import com.bukcase.authz.admin.ProfileAdminService;
import com.bukcase.authz.api.AccessDeniedException;
import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import com.bukcase.authz.engine.CompiledPermissions;
import com.bukcase.authz.engine.Grant;
import com.bukcase.authz.engine.PermissionProvider;
import com.bukcase.identity.CurrentUser;
import com.bukcase.modules.assets.Asset;
import com.bukcase.modules.assets.AssetRepository;
import com.bukcase.modules.assets.AssetService;
import com.bukcase.modules.complaints.Complaint;
import com.bukcase.modules.complaints.ComplaintService;
import com.bukcase.modules.vacations.VacationRequest;
import com.bukcase.modules.vacations.VacationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Interactive menu that drives the engine through in-process method calls. There is no HTTP layer:
 * the application runs as a plain JVM process, like the monolith described in the business case.
 */
@Component
@ConditionalOnProperty(name = "demo.console.enabled", havingValue = "true")
class DemoConsole implements CommandLineRunner {

    private static final long ADMIN_PROFILE = 1;
    private static final long ONLY_ADMIN_USER = 1;

    private final AssetService assets;
    private final AssetRepository assetRepository;
    private final ComplaintService complaints;
    private final VacationService vacations;
    private final Authorizer authorizer;
    private final PermissionProvider permissions;
    private final ProfileAdminService admin;
    private final DemoCatalog catalog;
    private final MeterRegistry meters;
    private final LoggingSystem logging;
    private final ApplicationContext context;

    private final Scanner input = new Scanner(System.in);
    private long currentUser = 1;
    private boolean sqlVisible;

    DemoConsole(AssetService assets, AssetRepository assetRepository, ComplaintService complaints,
                VacationService vacations, Authorizer authorizer, PermissionProvider permissions,
                ProfileAdminService admin, DemoCatalog catalog, MeterRegistry meters, LoggingSystem logging,
                ApplicationContext context) {
        this.assets = assets;
        this.assetRepository = assetRepository;
        this.complaints = complaints;
        this.vacations = vacations;
        this.authorizer = authorizer;
        this.permissions = permissions;
        this.admin = admin;
        this.catalog = catalog;
        this.meters = meters;
        this.logging = logging;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        System.out.println();
        System.out.println("==================================================================");
        System.out.println(" Authorization engine demo — in-process calls, no HTTP server");
        System.out.println("==================================================================");
        while (true) {
            printMenu();
            String choice = ask("Option");
            if (choice == null || choice.equals("0")) {
                break;
            }
            try {
                dispatch(choice);
            } catch (AccessDeniedException e) {
                System.out.println("  DENIED: " + e.getMessage());
            } catch (AdminRuleViolation e) {
                System.out.println("  REJECTED: " + e.getMessage());
            } catch (NoSuchElementException | IllegalArgumentException e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
        }
        System.out.println("Bye.");
        System.exit(SpringApplication.exit(context));
    }

    private void printMenu() {
        DemoCatalog.DemoUser user = catalog.user(currentUser);
        System.out.println();
        System.out.println("Current user: [" + user.id() + "] " + user.username() + " — " + user.profiles());
        System.out.println("  1. Switch current user            8. Admin: grant a cell (new profile) to current user");
        System.out.println("  2. List assets                    9. Admin: remove demo profiles");
        System.out.println("  3. List complaints               10. Admin: clone a profile to another area");
        System.out.println("  4. List vacation requests        11. Admin: try to remove the last administrator");
        System.out.println("  5. Check access to an asset      12. Run an async job as current user");
        System.out.println("  6. Approve a vacation request    13. Show metrics");
        System.out.println("  7. Show compiled permissions     14. Toggle SQL logging (now " + (sqlVisible ? "ON" : "OFF") + ")");
        System.out.println("  0. Exit");
    }

    private void dispatch(String choice) {
        switch (choice) {
            case "1" -> switchUser();
            case "2" -> listAssets();
            case "3" -> listComplaints();
            case "4" -> listVacations();
            case "5" -> checkAsset();
            case "6" -> approveVacation();
            case "7" -> showPermissions();
            case "8" -> grantCell();
            case "9" -> removeDemoProfiles();
            case "10" -> cloneProfile();
            case "11" -> removeLastAdministrator();
            case "12" -> runAsyncJob();
            case "13" -> showMetrics();
            case "14" -> toggleSql();
            default -> System.out.println("  Unknown option");
        }
    }

    private void switchUser() {
        catalog.users().forEach(user -> System.out.printf("  [%d] %-20s %s%n", user.id(), user.username(), user.profiles()));
        long id = askLong("User id");
        catalog.user(id);
        currentUser = id;
    }

    private void listAssets() {
        List<Asset> visible = asCurrentUser(assets::list);
        System.out.println("  " + visible.size() + " asset(s) visible:");
        visible.forEach(asset -> System.out.printf("  [%d] %-20s %-10s assigned to %-22s area: %s%n",
                asset.getId(), asset.getName(), asset.getCategory().getName(),
                asset.getEmployee() == null ? "-" : asset.getEmployee().getFullName(),
                catalog.areaName(asset.getAreaId())));
    }

    private void listComplaints() {
        List<Complaint> visible = asCurrentUser(complaints::list);
        System.out.println("  " + visible.size() + " complaint(s) visible:");
        visible.forEach(complaint -> System.out.printf("  [%d] %-15s %-40s area: %s%n",
                complaint.getId(), complaint.getType().getName(), complaint.getDescription(),
                catalog.areaName(complaint.getAreaId())));
    }

    private void listVacations() {
        List<VacationRequest> visible = asCurrentUser(vacations::list);
        System.out.println("  " + visible.size() + " vacation request(s) visible:");
        visible.forEach(request -> System.out.printf("  [%d] %-20s %s → %s  %-9s area: %s%n",
                request.getId(), request.getEmployee().getFullName(), request.getStartDate(), request.getEndDate(),
                request.getStatus(), catalog.areaName(request.getEmployee().getAreaId())));
    }

    private void checkAsset() {
        long id = askLong("Asset id");
        Asset asset = assetRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Asset " + id + " not found"));
        boolean read = asCurrentUser(() -> authorizer.can(AccessLevel.READ, asset));
        boolean write = asCurrentUser(() -> authorizer.can(AccessLevel.WRITE, asset));
        System.out.printf("  %s (%s, area: %s) → READ: %s, WRITE: %s%n", asset.getName(), asset.getCategory().getName(),
                catalog.areaName(asset.getAreaId()), read ? "allowed" : "denied", write ? "allowed" : "denied");
    }

    private void approveVacation() {
        long id = askLong("Vacation request id");
        VacationRequest approved = asCurrentUser(() -> vacations.approve(id));
        System.out.println("  Approved request " + approved.getId() + " of " + approved.getEmployee().getFullName());
    }

    private void showPermissions() {
        CompiledPermissions compiled = permissions.forUser(currentUser);
        if (compiled.admin()) {
            System.out.println("  Administrator: every area, every module (including future ones)");
            return;
        }
        if (compiled.grants().isEmpty()) {
            System.out.println("  No grants: the user cannot access anything");
            return;
        }
        for (Grant grant : compiled.grants()) {
            System.out.printf("  %-5s on %-28s in area %s (and sub-areas)%n", grant.level(),
                    catalog.resourceCode(grant.resourceId()), catalog.areaName(grant.areaId()));
        }
    }

    private void grantCell() {
        catalog.areaTree().forEach(line -> System.out.println("  " + line));
        long areaId = askLong("Area id");
        catalog.resourceTree().forEach(line -> System.out.println("  " + line));
        long resourceId = askLong("Resource id");
        AccessLevel level = "W".equalsIgnoreCase(ask("Level (R/W)")) ? AccessLevel.WRITE : AccessLevel.READ;

        String name = "Demo " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        long profileId = admin.createProfile(name);
        admin.grant(profileId, areaId, resourceId, level);
        admin.assign(currentUser, profileId);
        System.out.println("  Created profile '" + name + "' with " + level + " on " + catalog.resourceCode(resourceId)
                + " in " + catalog.areaName(areaId) + ", assigned to the current user.");
        System.out.println("  The cache version was bumped after commit: the change is already in effect.");
    }

    private void removeDemoProfiles() {
        List<Long> ids = catalog.demoProfileIds();
        ids.forEach(admin::deleteProfile);
        System.out.println("  Removed " + ids.size() + " demo profile(s).");
    }

    private void cloneProfile() {
        catalog.profiles().stream().filter(profile -> !profile.system())
                .forEach(profile -> System.out.printf("  [%d] %s%n", profile.id(), profile.name()));
        long sourceId = askLong("Profile to clone");
        catalog.areaTree().forEach(line -> System.out.println("  " + line));
        long fromArea = askLong("Replace area id");
        long toArea = askLong("With area id");
        String name = "Demo clone " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        long cloneId = admin.cloneProfile(sourceId, name, Map.of(fromArea, toArea));
        System.out.println("  Created '" + name + "' (id " + cloneId + ").");
        if ("y".equalsIgnoreCase(ask("Assign it to the current user? (y/n)"))) {
            admin.assign(currentUser, cloneId);
            System.out.println("  Assigned.");
        }
    }

    private void removeLastAdministrator() {
        System.out.println("  Removing the Administrator profile from " + catalog.user(ONLY_ADMIN_USER).username() + "...");
        admin.unassign(ONLY_ADMIN_USER, ADMIN_PROFILE);
        System.out.println("  Removed (another administrator exists). Restoring it.");
        admin.assign(ONLY_ADMIN_USER, ADMIN_PROFILE);
    }

    private void runAsyncJob() {
        long enqueuedBy = currentUser;
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            String result = worker.submit(() -> CurrentUser.runAs(enqueuedBy, () ->
                    "thread '" + Thread.currentThread().getName() + "' ran as user " + CurrentUser.id()
                            + " and saw " + assets.list().size() + " asset(s)")).get();
            System.out.println("  Job with enqueuing user: " + result);

            try {
                worker.submit(() -> assets.list()).get();
            } catch (ExecutionException e) {
                System.out.println("  Job without user: rejected with " + e.getCause().getClass().getSimpleName());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            System.out.println("  Job failed: " + e.getCause().getMessage());
        } finally {
            worker.shutdown();
        }
    }

    private void showMetrics() {
        printTimer("authz.check", "Point checks");
        printTimer("authz.compile", "Compilations (cache miss)");
        for (Counter counter : meters.find("authz.cache").counters()) {
            System.out.printf("  Cache %-4s level=%-6s %,.0f%n", counter.getId().getTag("result"),
                    counter.getId().getTag("level"), counter.count());
        }
        for (Counter counter : meters.find("authz.denied").counters()) {
            System.out.printf("  Denied on %-25s %,.0f%n", counter.getId().getTag("resource"), counter.count());
        }
    }

    private void printTimer(String name, String label) {
        Timer timer = meters.find(name).timer();
        if (timer == null || timer.count() == 0) {
            System.out.println("  " + label + ": no samples yet");
            return;
        }
        HistogramSnapshot snapshot = timer.takeSnapshot();
        StringBuilder line = new StringBuilder(String.format("  %s: %d calls, mean %.1f µs", label, snapshot.count(),
                snapshot.mean(TimeUnit.MICROSECONDS)));
        for (ValueAtPercentile percentile : snapshot.percentileValues()) {
            line.append(String.format(", p%.0f %.1f µs", percentile.percentile() * 100,
                    percentile.value(TimeUnit.MICROSECONDS)));
        }
        System.out.println(line);
    }

    private void toggleSql() {
        sqlVisible = !sqlVisible;
        logging.setLogLevel("org.hibernate.SQL", sqlVisible ? LogLevel.DEBUG : LogLevel.WARN);
        System.out.println("  SQL logging " + (sqlVisible ? "ON: list queries will show the generated filter" : "OFF"));
    }

    private <T> T asCurrentUser(Supplier<T> action) {
        return CurrentUser.runAs(currentUser, action);
    }

    private String ask(String label) {
        System.out.print("  " + label + ": ");
        System.out.flush();
        return input.hasNextLine() ? input.nextLine().trim() : null;
    }

    private long askLong(String label) {
        String value = ask(label);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("Expected a number, got '" + value + "'");
        }
    }
}
