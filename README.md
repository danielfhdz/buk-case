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
