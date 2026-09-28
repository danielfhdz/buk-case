package com.bukcase.benchmark;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.Authorizer;
import com.bukcase.authz.cache.AuthzVersion;
import com.bukcase.authz.engine.PermissionCompiler;
import com.bukcase.identity.CurrentUser;
import com.bukcase.modules.assets.Asset;
import com.bukcase.modules.assets.AssetRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Volume benchmark on an isolated {@code bench} schema: ~1,000 areas, 50,000 employees, 100,000
 * assets, 1,000 profiles with ~10,000 grants and 1,000 users. Excluded from the regular build;
 * run it explicitly. Results are printed and written to {@code target/benchmark-results.md}.
 */
@Tag("benchmark")
@SpringBootTest(properties = {
        "spring.docker.compose.skip.in-tests=false",
        "spring.datasource.hikari.schema=bench",
        "spring.flyway.default-schema=bench",
        "spring.flyway.schemas=bench",
        "spring.jpa.properties.hibernate.default_schema=bench",
        "authz.cache.namespace=bench"
})
class AuthorizationBenchmarkTest {

    private static final long ANDRES_AREA_SCOPED = 2;
    private static final long PEDRO_COMPANY_WIDE = 4;
    private static final long JORGE_ENTITY_SCOPED = 5;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Authorizer authorizer;

    @Autowired
    AssetRepository assets;

    @Autowired
    PermissionCompiler compiler;

    @Autowired
    AuthzVersion version;

    private final StringBuilder report = new StringBuilder();

    @Test
    void measure() throws IOException {
        generateDataIfMissing();
        version.bump();
        describeVolume();

        long benchUser = jdbc.queryForObject("SELECT MIN(id) FROM users WHERE username LIKE 'bench.%'", Long.class);
        measurePointChecks(benchUser);
        measureCompilation();
        measureListFiltering("Company-wide grant (Pedro)", PEDRO_COMPANY_WIDE);
        measureListFiltering("Area-scoped grant (Andres)", ANDRES_AREA_SCOPED);
        measureListFiltering("Entity-scoped grant (Jorge)", JORGE_ENTITY_SCOPED);
        measureListFiltering("Random profiles, ~20 grants (bench user)", benchUser);

        System.out.println(report);
        Path output = Path.of("target", "benchmark-results.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString());
    }

    private void generateDataIfMissing() {
        Integer employees = jdbc.queryForObject("SELECT COUNT(*) FROM employees", Integer.class);
        if (employees != null && employees > 1_000) {
            return;
        }
        jdbc.execute("SELECT setseed(0.42)");
        jdbc.execute("INSERT INTO areas (name, parent_id) SELECT 'L1-' || g, 1 FROM generate_series(1, 10) g");
        jdbc.execute("""
                INSERT INTO areas (name, parent_id)
                SELECT 'L2-' || a.id || '-' || g, a.id FROM areas a CROSS JOIN generate_series(1, 10) g
                WHERE a.name LIKE 'L1-%'""");
        jdbc.execute("""
                INSERT INTO areas (name, parent_id)
                SELECT 'L3-' || a.id || '-' || g, a.id FROM areas a CROSS JOIN generate_series(1, 9) g
                WHERE a.name LIKE 'L2-%'""");
        jdbc.execute("INSERT INTO positions (name, area_id) SELECT 'Position ' || id, id FROM areas WHERE name LIKE 'L3-%'");
        jdbc.execute("""
                INSERT INTO employees (full_name, position_id)
                SELECT 'Employee ' || g, pos.ids[1 + g % array_length(pos.ids, 1)]
                FROM (SELECT array_agg(id ORDER BY id) AS ids FROM positions WHERE name LIKE 'Position %') pos,
                     generate_series(1, 50000) g""");
        jdbc.execute("""
                INSERT INTO assets (name, category_id, employee_id)
                SELECT 'Asset ' || g, 1 + g % 3, CASE WHEN g % 20 = 0 THEN NULL ELSE emp.lo + g % (emp.hi - emp.lo + 1) END
                FROM (SELECT MIN(id) AS lo, MAX(id) AS hi FROM employees WHERE full_name LIKE 'Employee %') emp,
                     generate_series(1, 100000) g""");
        jdbc.execute("INSERT INTO profiles (name) SELECT 'Bench profile ' || g FROM generate_series(1, 1000) g");
        jdbc.execute("""
                INSERT INTO profile_grants (profile_id, area_id, resource_id, access_level)
                SELECT p.id,
                       a.ids[1 + floor(random() * array_length(a.ids, 1))::int],
                       r.ids[1 + floor(random() * array_length(r.ids, 1))::int],
                       1 + (random() < 0.5)::int
                FROM (SELECT id FROM profiles WHERE name LIKE 'Bench profile %') p,
                     (SELECT array_agg(id) AS ids FROM areas WHERE name LIKE 'L%') a,
                     (SELECT array_agg(id) AS ids FROM resources) r,
                     generate_series(1, 10)
                ON CONFLICT DO NOTHING""");
        jdbc.execute("INSERT INTO users (username) SELECT 'bench.' || g FROM generate_series(1, 1000) g");
        jdbc.execute("""
                INSERT INTO user_profiles (user_id, profile_id)
                SELECT u.id, p.id
                FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM users WHERE username LIKE 'bench.%') u
                JOIN (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM profiles WHERE name LIKE 'Bench profile %') p
                  ON p.rn = u.rn OR p.rn = u.rn % 1000 + 1""");
        jdbc.execute("ANALYZE");
    }

    private void describeVolume() {
        report.append("# Authorization benchmark\n\n## Data volume\n\n| Table | Rows |\n|---|---|\n");
        for (String table : List.of("areas", "area_closure", "employees", "assets", "profiles", "profile_grants",
                "users", "user_profiles")) {
            report.append("| ").append(table).append(" | ")
                    .append(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).append(" |\n");
        }
    }

    private void measurePointChecks(long userId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM assets ORDER BY random() LIMIT 2000", Long.class);
        List<Asset> sample = assets.findAllById(ids);

        long[] nanos = CurrentUser.runAs(userId, () -> {
            sample.forEach(asset -> authorizer.can(AccessLevel.READ, asset));
            long[] samples = new long[20_000];
            for (int i = 0; i < samples.length; i++) {
                Asset asset = sample.get(i % sample.size());
                long start = System.nanoTime();
                authorizer.can(AccessLevel.READ, asset);
                samples[i] = System.nanoTime() - start;
            }
            return samples;
        });
        section("Point check `can(READ, asset)` (warm cache, 20,000 calls)", nanos);
    }

    private void measureCompilation() {
        List<Long> users = jdbc.queryForList("SELECT id FROM users WHERE username LIKE 'bench.%' LIMIT 200", Long.class);
        long[] nanos = new long[users.size()];
        for (int i = 0; i < users.size(); i++) {
            long userId = users.get(i);
            nanos[i] = time(() -> compiler.compile(userId));
        }
        section("Permission compilation on cache miss (200 users)", nanos);
    }

    private void measureListFiltering(String label, long userId) {
        CurrentUser.runAs(userId, () -> {
            long visible = assets.count(authorizer.readable(Asset.class));
            long[] count = new long[20];
            long[] page = new long[20];
            for (int i = 0; i < 3; i++) {
                assets.count(authorizer.readable(Asset.class));
            }
            for (int i = 0; i < count.length; i++) {
                count[i] = time(() -> assets.count(authorizer.readable(Asset.class)));
                page[i] = time(() -> assets.findAll(authorizer.readable(Asset.class), PageRequest.of(0, 50)));
            }
            section("List filtering: " + label + " — " + visible + " of 100k+ assets visible", count, page);
        });
    }

    private static long time(Supplier<?> work) {
        long start = System.nanoTime();
        work.get();
        return System.nanoTime() - start;
    }

    private void section(String title, long[] nanos) {
        report.append("\n## ").append(title).append("\n\n| p50 | p95 | p99 | max |\n|---|---|---|---|\n")
                .append(row(nanos)).append('\n');
    }

    private void section(String title, long[] countNanos, long[] pageNanos) {
        report.append("\n## ").append(title)
                .append("\n\n| Query | p50 | p95 | p99 | max |\n|---|---|---|---|---|\n")
                .append("| `count(readable)` ").append(row(countNanos)).append('\n')
                .append("| first page of 50 ").append(row(pageNanos)).append('\n');
    }

    private static String row(long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        List<String> cells = new ArrayList<>();
        for (double q : new double[] {0.50, 0.95, 0.99, 1.0}) {
            int index = Math.min(sorted.length - 1, (int) Math.ceil(q * sorted.length) - 1);
            cells.add(format(sorted[Math.max(0, index)]));
        }
        return "| " + String.join(" | ", cells) + " |";
    }

    private static String format(long nanos) {
        return nanos < 1_000_000
                ? String.format(Locale.ROOT, "%.1f µs", nanos / 1_000.0)
                : String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
    }
}
