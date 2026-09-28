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
| Actuator / Micrometer | Authorization engine metrics |

## Requirements

- JDK 21 (IntelliJ can download it: *File → Project Structure → SDK → Download JDK → Temurin 21*)
- Docker Desktop

## Running

1. Open the folder in IntelliJ IDEA (it detects `pom.xml` as a Maven project).
2. Run `AuthzPocApplication`.

On startup Spring Boot starts PostgreSQL and Redis from `compose.yaml` and Flyway applies the migrations.
They can also be started manually with `docker compose up -d`.

Health check: `http://localhost:8080/actuator/health`

## Trying it out

Every request identifies the user with the `X-User-Id` header (stand-in for the existing authentication).

| User id | Demo user | Profile |
|---|---|---|
| 1 | Laura | Administrator (everything) |
| 2 | Andres | Commercial Management, read-only (assets, vacations, documents) |
| 3 | Carolina | Vacations, write, North Sales only |
| 4 | Pedro | Assets write and Documents read, whole company |
| 5 | Jorge | Assets write, Computers and Phones only |
| 7 | Elena | Complaints: Harassment and Discrimination |
| 8 | Tomas | Complaints: Fraud |
| 9 | Nicolas | No profile |

```
curl -H "X-User-Id: 5" http://localhost:8080/assets
curl -H "X-User-Id: 3" http://localhost:8080/vacations
curl -H "X-User-Id: 7" http://localhost:8080/complaints
curl -H "X-User-Id: 5" http://localhost:8080/authz/me
```

Metrics: `/actuator/metrics/authz.check`, `authz.compile`, `authz.cache`, `authz.denied`.

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
└── modules        # Sample modules consuming the engine
    ├── assets     # Asset Management
    ├── complaints # Whistleblowing Channel
    └── vacations  # Vacations
```
