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
- List filtering is one `EXISTS` subquery joining the user's grants with both closure tables, regardless of how many grants the user has.
- **Alternatives for the document:**
  - Separate Area/SubArea and Module/Entity tables with 5 references per grant: fixed depth, nullable columns, `OR` in every query.
  - Adjacency list with recursive CTE: no extra table, but recursion on every check.
  - Materialized path (`/1/2/3/`): simple prefix queries, but moving nodes rewrites paths and prefix `LIKE` is less index-friendly.
  - Nested sets: fast reads, very expensive writes.
- Known limitation: moving a node to another parent requires rebuilding its closure rows (not covered by the insert trigger; done in the admin service).
- Records without an area (e.g. an unassigned asset) are treated as belonging to the root area, so only company-wide grants cover them.

## D8. Hybrid evaluation: compiled permissions for point checks, SQL for lists

- **Point checks** (`can`/`check`): the user's grants are compiled once (union of all profiles, strongest level per cell), cached in Redis, and evaluated in memory against the ancestor paths of the record's area and resource. Trees are held in memory (`TreeIndex`), reloaded when the version changes.
- **List filtering** (`readable`/`writable`): a JPA `Specification` with one correlated `EXISTS` over grants and both closure tables. Filtering never loads records into memory.
- Admins short-circuit both paths.
- **Alternative for the document:** everything in SQL (simpler, always fresh, but one DB round trip per point check); everything in memory (expanding area id lists into `IN (...)` clauses, which grows with company size).

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
