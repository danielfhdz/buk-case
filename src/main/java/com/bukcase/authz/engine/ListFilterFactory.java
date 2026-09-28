package com.bukcase.authz.engine;

import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.AuthorizationDescriptor;
import com.bukcase.authz.domain.AreaClosure;
import com.bukcase.authz.domain.ResourceClosure;
import com.bukcase.identity.CurrentUser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Turns a user's compiled grants into a SQL filter for one entity type.
 *
 * <p>The filter is built from the cached grants rather than joined against the grant tables: each
 * condition is an uncorrelated {@code IN (SELECT descendant_id FROM ..._closure ...)} that the
 * database evaluates once and probes as a hash set, so the cost per row is constant and does not
 * depend on how many grants the user has. Grants on other modules are discarded, grants sharing an
 * area are merged, and conditions covering a whole axis are dropped entirely.
 */
@Component
public class ListFilterFactory {

    private final PermissionProvider permissions;
    private final TreeIndex trees;

    public ListFilterFactory(PermissionProvider permissions, TreeIndex trees) {
        this.permissions = permissions;
        this.trees = trees;
    }

    public <T> Specification<T> create(AuthorizationDescriptor<T> descriptor, AccessLevel level) {
        return (root, query, cb) -> {
            CompiledPermissions compiled = permissions.forUser(CurrentUser.id());
            if (compiled.admin()) {
                return cb.conjunction();
            }

            long module = trees.idOf(descriptor.module());
            Map<Long, Set<Long>> resourcesByArea = relevantGrants(compiled, level, module);
            if (resourcesByArea.isEmpty()) {
                return cb.disjunction();
            }

            long rootArea = trees.rootAreaId();
            boolean needsArea = resourcesByArea.keySet().stream().anyMatch(area -> area != rootArea);
            boolean needsResource = resourcesByArea.values().stream().anyMatch(resources -> !resources.contains(module));
            if (!needsArea && !needsResource) {
                return cb.conjunction();
            }
            Expression<Long> recordArea = needsArea ? descriptor.areaId(root, cb) : null;
            Expression<Long> recordResource = needsResource ? descriptor.resourceId(root, cb) : null;

            List<Predicate> alternatives = new ArrayList<>();
            for (Map.Entry<Long, Set<Long>> cell : resourcesByArea.entrySet()) {
                List<Predicate> conditions = new ArrayList<>(2);
                if (cell.getKey() != rootArea) {
                    conditions.add(recordArea.in(areaSubtree(query, cb, cell.getKey())));
                }
                if (!cell.getValue().contains(module)) {
                    conditions.add(recordResource.in(resourceSubtrees(query, cb, cell.getValue())));
                }
                if (conditions.isEmpty()) {
                    return cb.conjunction();
                }
                alternatives.add(cb.and(conditions.toArray(Predicate[]::new)));
            }
            return cb.or(alternatives.toArray(Predicate[]::new));
        };
    }

    /**
     * Grants that satisfy the level and touch the module, grouped by area. A grant on the module or
     * above it is recorded as the module itself, which later means "no resource condition".
     */
    private Map<Long, Set<Long>> relevantGrants(CompiledPermissions compiled, AccessLevel level, long module) {
        Map<Long, Set<Long>> resourcesByArea = new LinkedHashMap<>();
        for (Grant grant : compiled.grants()) {
            if (!grant.level().satisfies(level)) {
                continue;
            }
            boolean coversModule = trees.isResourceAncestorOrSelf(grant.resourceId(), module);
            boolean insideModule = trees.isResourceAncestorOrSelf(module, grant.resourceId());
            if (coversModule || insideModule) {
                resourcesByArea.computeIfAbsent(grant.areaId(), area -> new LinkedHashSet<>())
                        .add(coversModule ? module : grant.resourceId());
            }
        }
        return resourcesByArea;
    }

    private static Subquery<Long> areaSubtree(CriteriaQuery<?> query, CriteriaBuilder cb, long areaId) {
        Subquery<Long> subtree = query.subquery(Long.class);
        Root<AreaClosure> closure = subtree.from(AreaClosure.class);
        return subtree.select(closure.<Long>get("descendantId")).where(cb.equal(closure.get("ancestorId"), areaId));
    }

    private static Subquery<Long> resourceSubtrees(CriteriaQuery<?> query, CriteriaBuilder cb, Set<Long> resourceIds) {
        Subquery<Long> subtree = query.subquery(Long.class);
        Root<ResourceClosure> closure = subtree.from(ResourceClosure.class);
        return subtree.select(closure.<Long>get("descendantId")).where(closure.get("ancestorId").in(resourceIds));
    }
}
