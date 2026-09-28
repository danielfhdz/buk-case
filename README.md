# authz-poc — Module, area and entity-type authorization

Proof of concept of an authorization engine for a multi-tenant SaaS monolith:

- **Level 1:** per-module permissions (none / read / write) with an optional organizational-area scope that includes sub-areas.
- **Level 2:** granular restrictions by entity type or category inside a module, extensible to other criteria.

> The technical document (requirements, alternatives, decisions, risks and metrics) is delivered separately.

## Stack

| Component | Purpose |
|---|---|
| Java 21 + Spring Boot 3.5 | Application (monolith) |
| PostgreSQL 16 | Relational database |
| Flyway | Versioned schema migrations (explicit indexes) |
| Redis 7 | Shared cache of compiled permissions |
| Micrometer | Authorization engine metrics (shown in the console menu) |

## Requirements

- JDK 21 (IntelliJ can download it: *File → Project Structure → SDK → Download JDK → Temurin 21*)
- Docker Desktop

## Running

1. Open the folder in IntelliJ IDEA (it detects `pom.xml` as a Maven project).
2. Run `AuthzPocApplication`.

On startup Spring Boot starts PostgreSQL and Redis from `compose.yaml` and Flyway applies the migrations.
They can also be started manually with `docker compose up -d`.

## Trying it out

There is no HTTP server: the application runs as a plain JVM process and opens an interactive console
menu. The menu is just another in-process consumer that calls the same services and `Authorizer`
methods a feature module would call.

| User id | Demo user | Profile |
|---|---|---|
| 1 | laura.general | Administrator (everything) |
| 2 | andres.commercial | Commercial Management, read-only (assets, vacations, documents) |
| 3 | carolina.north | Vacations, write, North Sales only |
| 4 | pedro.assets | Assets write and Documents read, whole company |
| 5 | jorge.it | Assets write, Computers and Phones only |
| 7 | elena.compliance | Complaints: Harassment and Discrimination |
| 8 | tomas.compliance | Complaints: Fraud |
| 9 | nicolas.noaccess | No profile |

Menu actions: switch user, list assets / complaints / vacation requests, point checks (read/write) on an
asset, approve a vacation request, show compiled permissions, administration (grant a cell, clone a profile
to another area, try to remove the last administrator), run an async job as the current user, show metrics
and toggle SQL logging to see the generated filters.

## Tests

`AuthorizationScenariosTest` and `ProfileAdminServiceTest` run the business scenarios and administration rules against PostgreSQL and Redis (started from `compose.yaml` if needed).

## Benchmark

`AuthorizationBenchmarkTest` is tagged `benchmark` and excluded from the regular build; run it explicitly from the IDE. It generates ~1,000 areas, 50,000 employees, 100,000 assets and ~10,000 grants in a separate `bench` schema and writes the results to `target/benchmark-results.md`.

| Measurement (p50) | Result |
|---|---|
| Point check, warm cache | ~6 µs |
| Permission compilation on cache miss | ~4 ms |
| `count(readable)` over 100k assets, realistic profiles | 14-34 ms |
| First page of 50, realistic profiles | 25-50 ms |

## Design decisions

See [`docs/decisions.md`](docs/decisions.md) for every decision, the alternatives considered and the trade-offs.

## Structure

```
com.bukcase
├── authz          # Authorization engine (feature modules only see authz.api)
│   ├── api        # Point checks, list filtering, annotations
│   ├── domain     # Profiles, permissions, assignments, area scopes, restrictions
│   ├── engine     # Compilation and evaluation of effective permissions
│   ├── cache      # Redis cache and invalidation
│   └── admin      # Profile administration (invariant: always one administrator)
├── org            # Hierarchical areas and employees
├── identity       # Current user (equivalent to User::GetCurrent)
├── modules        # Sample modules consuming the engine
    ├── assets     # Asset Management
    ├── complaints # Whistleblowing Channel
│   └── vacations  # Vacations
└── demo           # Interactive console menu (in-process consumer, no HTTP)
```
