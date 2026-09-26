# CLAUDE.md

## Project context

Proof of concept of an authorization engine for a multi-tenant SaaS monolith (people management platform).

- **Level 1:** per-module access levels (none / read / write) with an optional organizational-area scope that includes all sub-areas.
- **Level 2:** granular restrictions by entity type or category inside a module, extensible to other criteria.
- Consumers are the development teams that build modules: they must get authorization without writing authorization logic.
- The API is in-process (method calls), not REST. Web endpoints exist only for metrics and demos.
- Tenancy is database-per-tenant: within a request, all data is already scoped to one tenant.

## Stack

Java 21, Spring Boot 3.5, PostgreSQL 16, Flyway, Redis 7, Spring AOP, Actuator/Micrometer, Maven.
`compose.yaml` provides PostgreSQL and Redis; Spring Boot starts it automatically on run.

## Architecture rules

- Feature modules (`com.bukcase.modules.*`) depend only on `com.bukcase.authz.api`, never on engine, cache or domain internals.
- The database schema is owned by Flyway migrations (`src/main/resources/db/migration`). Hibernate only validates (`ddl-auto: validate`); never change it to create/update.
- List filtering must happen in SQL, never by loading records into memory and filtering in Java.

## Coding conventions

- **Everything in English:** code, identifiers, comments, log messages, exceptions, migrations, commit messages and documentation files in this repo.
- **Comments only where they add value:**
  - Allowed: Javadoc on classes and public methods whose purpose or contract is not obvious from the name and signature, and short notes explaining a non-obvious *why* (a trade-off, an invariant, a performance reason).
  - Not allowed: comments inside method bodies, comments that restate what the code does, section-divider comments, commented-out code, TODOs without context.
  - Prefer expressive names and small methods over comments.
