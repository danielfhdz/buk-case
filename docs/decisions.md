# Design decisions

Running log of design decisions. Each entry records the choice, the reason, and the alternatives kept for the technical document.

## D1. Permission model: sparse matrix Area x Resource

- A permission (grant) is a cell `(area, resource, access level)`.
- **X axis:** organizational area tree. A grant on an area covers all its sub-areas.
- **Y axis:** resource tree `module > entity > ...`. A grant on a module covers all its entities.
- Only granted cells are stored (sparse); inheritance is resolved through both trees.
- Level 1 = grant on a module node. Level 2 = grant on an entity node. No granular grants means full module access (backward compatible by construction).

## D2. Access levels: READ and WRITE only

- `WRITE` implies `READ`. Anything not granted is denied.
- Administrator is a system profile flag (`is_admin`), not a cell: it covers every area and every current and future module.
- **Alternative for the document:** richer permission types / conditions, e.g. "read excluding records where the user is involved" (conflict of interest in the Whistleblowing Channel), modeled as module policies separate from the access level.

## D3. Areas live in the profile

- A profile is a self-contained matrix (areas + resources + levels): easy to understand, audit and display.
- A `cloneProfile(source, newName, areaMapping)` operation creates similar profiles for other areas.
- Trade-off: more profiles; clones do not follow later changes to the source.
- **Alternative for the document:** the profile defines resources and levels, and the area scope is set when assigning the profile to a user (fewer, reusable profiles; area lives in the assignment).

## D4. Granularity through entities, not attribute filters

- Finer granularity is expressed by creating entity nodes in the resource tree (e.g. `Assets > Computers`, `Complaints > Fraud`).
- Each record resolves to exactly one resource node (e.g. an asset through its category).
- **Alternative for the document:** generic attribute/value grants (`attribute = category, value = COMPUTERS`) declared per module, which support future criteria without new entities.

## D5. Users have zero, one or many profiles

- A user's effective permissions are the union of the grants of all their profiles (most permissive wins).
- A user with no profiles has no access.
- Since areas live in the profile (D3), a person covering two areas gets two profiles instead of a combined one.

## D6. The position's area does not grant scope

- `User -> Employee -> Position -> Area` is organizational data: it tells which area an employee's records belong to (e.g. their vacation requests).
- Access scope always comes from explicit grants, never implicitly from the user's own position.
- **Alternative for the document:** derive a default scope from the user's position area (less configuration, less explicit and harder to audit).

## D7. Both axes are self-referencing trees with closure tables

- `areas` and `resources` each have a `parent_id`: any depth is supported, and "area/sub-area" or "module/entity" are just nodes at different levels.
- A grant has only three references: `profile_id`, `area_id`, `resource_id`. General permissions point to higher nodes.
- `area_closure` / `resource_closure` store every (ancestor, descendant) pair, maintained by insert triggers, so subtree checks are a single indexed join.
- **Alternatives for the document:**
  - Separate Area/SubArea and Module/Entity tables with 5 references per grant: fixed depth, nullable columns, `OR` in every query.
  - Adjacency list with recursive CTE: no extra table, but recursion on every check.
  - Materialized path (`/1/2/3/`): simple prefix queries, but moving nodes rewrites paths and prefix `LIKE` is less index-friendly.
  - Nested sets: fast reads, very expensive writes.
- Known limitation: moving a node to another parent requires rebuilding its closure rows (not covered by the insert trigger; done in the admin service).
- Records without an area (e.g. an unassigned asset) are treated as belonging to the root area, so only company-wide grants cover them.

## D8. Hybrid evaluation: compiled permissions for point checks, SQL for lists

- **Point checks** (`can`/`check`): the user's grants are compiled once (union of all profiles, strongest level per cell), cached in Redis, and evaluated in memory against the ancestor paths of the record's area and resource. Trees are held in memory (`TreeIndex`), reloaded when the version changes.
- **List filtering** (`readable`/`writable`): a JPA `Specification` built from the same compiled grants (see D13). Filtering never loads records into memory.
- Admins short-circuit both paths.
- **Alternative for the document:** everything in SQL (simpler, always fresh, but one DB round trip per point check).

## D9. Invalidation by tenant-wide version

- A Redis counter `authz:{tenant}:version` is part of every cache key. Any configuration change bumps it; old entries become unreachable and expire by TTL (1 h).
- The bump happens **after commit**; bumping inside the transaction would let a concurrent reader cache the old data under the new version.
- Each instance re-reads the version at most every `authz.version-refresh-ms` (500 ms): that is the maximum propagation delay of a permission change across instances.
- **Alternative for the document:** targeted invalidation of affected users only (fewer recompilations, more complex and easier to get wrong).

## D10. Module integration contract

- A module team writes one `AuthorizationDescriptor<T>` per protected entity (how a record reaches its area and its resource node), then uses `Authorizer.readable(...)`, `Authorizer.check(...)` or `@RequiresAccess`.
- No authorization logic lives in modules; they never touch grants, trees or caches.

## D11. Descriptors expose area and resource as simple expressions

- Found while testing: when a descriptor navigates a **nullable** association (asset -> employee -> position), Hibernate relocates those joins inside the authorization `EXISTS` as inner joins, so records with a null association (unassigned assets) are silently excluded.
- Rule for module teams: the area and resource expressions should be plain columns or derived attributes (`@Formula`), not multi-hop navigation through nullable associations. Non-null associations (complaint -> type, request -> employee) are safe.
- Covered by an automated test (unassigned asset visible only through company-wide grants).

## D12. Two-level permission cache

- Level 1: in-process LRU (10,000 users per instance), no network and no deserialization.
- Level 2: Redis, shared by all instances, so a user's permissions are compiled once per version for the whole cluster.
- Both levels are keyed by the tenant version, so D9 invalidates them together and L1 adds no extra staleness beyond the version refresh interval.
- Metrics: `authz.cache{result=hit,level=local|redis}` and `authz.cache{result=miss}`.

## D13. List filtering driven by compiled grants (benchmark-driven)

- First version: one correlated `EXISTS` joining grants and both closure tables. Benchmark on 100k assets: **0.5-1.3 s per query**, even for a user who sees 4 assets. The cost was per row (correlated subquery plus a per-row formula), so it grew with table size, not with what the user can see.
- Current version: the filter is generated from the user's compiled grants:
  - grants that do not touch the entity's module are discarded;
  - grants are grouped by area;
  - a grant on the root area drops the area condition, a grant on the module (or above) drops the resource condition, and a grant with both returns no filter at all;
  - remaining conditions are uncorrelated `IN (SELECT descendant_id FROM *_closure WHERE ancestor_id ...)`, evaluated once by the database and probed as hash sets.
- Trade-off: the SQL shape now depends on the user's grants (bounded by the number of distinct areas with grants in that module, typically a handful), and list filtering uses cached grants, so it shares the propagation delay of D9.
- This also resolves D11 for list filtering: descriptor expressions are used only in the outer query, so explicit LEFT joins through nullable associations are preserved.
- Results (100k assets, p50 of `count(readable)`): company-wide 1,032 -> 24 ms; area-scoped 545 -> 14 ms; entity-scoped 822 -> 34 ms; synthetic user with ~20 random grants 514 -> 222 ms.
- Known limitation: cost grows with the number of distinct (area, resource) groups a user has in one module, because each adds an `OR` branch. Realistic profiles have one to three. Next optimizations if needed: denormalized, indexed `area_id` on protected records; a precomputed set of visible areas per user and module.
- The sample Assets module loads its associations with an `@EntityGraph` so the benchmark measures authorization, not N+1 loading.

## D14. No HTTP layer: console demo instead of REST endpoints

- The case requires the solution to talk to the rest of the application through in-code calls, not REST. The first demo exposed REST endpoints only as a test harness, which sent the wrong message.
- The web starter and every controller were removed. The application runs as a plain JVM process and opens an interactive console menu that calls services and `Authorizer` directly, like any feature module would.
- Metrics are still recorded in Micrometer and printed by the menu; in production they would be exported by the monolith's existing monitoring stack.
