package com.bukcase.authz.engine;

import com.bukcase.authz.api.AccessDeniedException;
import com.bukcase.authz.api.AccessLevel;
import com.bukcase.authz.api.AuthorizationDescriptor;
import com.bukcase.authz.api.Authorizer;
import com.bukcase.authz.domain.AreaClosure;
import com.bukcase.authz.domain.ProfileGrant;
import com.bukcase.authz.domain.ResourceClosure;
import com.bukcase.authz.domain.UserProfile;
import com.bukcase.identity.CurrentUser;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class DefaultAuthorizer implements Authorizer {

    private final PermissionProvider permissions;
    private final TreeIndex trees;
    private final DescriptorRegistry descriptors;
    private final AuthorizationMetrics metrics;

    public DefaultAuthorizer(PermissionProvider permissions, TreeIndex trees, DescriptorRegistry descriptors,
                             AuthorizationMetrics metrics) {
        this.permissions = permissions;
        this.trees = trees;
        this.descriptors = descriptors;
        this.metrics = metrics;
    }

    @Override
    public boolean can(AccessLevel level, Object record) {
        return metrics.timeCheck(() -> {
            AuthorizationDescriptor<Object> descriptor = descriptors.forRecord(record);
            return permissions.forUser(CurrentUser.id()).covers(level,
                    trees.areaPath(descriptor.areaIdOf(record)),
                    trees.resourcePath(descriptor.resourceIdOf(record)));
        });
    }

    @Override
    public void check(AccessLevel level, Object record) {
        if (!can(level, record)) {
            long resourceId = descriptors.forRecord(record).resourceIdOf(record);
            deny(level, trees.resourceCodeOf(resourceId));
        }
    }

    /**
     * True when the user holds the level on the resource, on an ancestor of it, or on any node
     * below it (e.g. a grant on Assets > Computers gives access to the Assets module).
     */
    @Override
    public boolean canAccessResource(AccessLevel level, String resourceCode) {
        return metrics.timeCheck(() -> {
            CompiledPermissions compiled = permissions.forUser(CurrentUser.id());
            if (compiled.admin()) {
                return true;
            }
            long resourceId = trees.idOf(resourceCode);
            return compiled.grants().stream()
                    .filter(grant -> grant.level().satisfies(level))
                    .anyMatch(grant -> trees.isResourceAncestorOrSelf(grant.resourceId(), resourceId)
                            || trees.isResourceAncestorOrSelf(resourceId, grant.resourceId()));
        });
    }

    @Override
    public void checkResource(AccessLevel level, String resourceCode) {
        if (!canAccessResource(level, resourceCode)) {
            deny(level, resourceCode);
        }
    }

    @Override
    public <T> Specification<T> readable(Class<T> entityType) {
        return filter(entityType, AccessLevel.READ);
    }

    @Override
    public <T> Specification<T> writable(Class<T> entityType) {
        return filter(entityType, AccessLevel.WRITE);
    }

    /**
     * Single correlated EXISTS over the user's grants and both closure tables, so the SQL shape
     * does not grow with the number of grants.
     */
    private <T> Specification<T> filter(Class<T> entityType, AccessLevel level) {
        AuthorizationDescriptor<T> descriptor = descriptors.forType(entityType);
        return (root, query, cb) -> {
            long userId = CurrentUser.id();
            CompiledPermissions compiled = permissions.forUser(userId);
            if (compiled.admin()) {
                return cb.conjunction();
            }
            if (!compiled.hasAny(level)) {
                return cb.disjunction();
            }

            Subquery<Integer> grants = query.subquery(Integer.class);
            Root<UserProfile> assignment = grants.from(UserProfile.class);
            Root<ProfileGrant> grant = grants.from(ProfileGrant.class);
            Root<AreaClosure> areaTree = grants.from(AreaClosure.class);
            Root<ResourceClosure> resourceTree = grants.from(ResourceClosure.class);

            grants.select(cb.literal(1)).where(
                    cb.equal(assignment.get("userId"), userId),
                    cb.equal(grant.get("profileId"), assignment.get("profileId")),
                    cb.greaterThanOrEqualTo(grant.<Short>get("accessLevel"), level.code()),
                    cb.equal(areaTree.get("ancestorId"), grant.get("areaId")),
                    cb.equal(areaTree.get("descendantId"),
                            cb.coalesce(descriptor.areaId(root, cb), trees.rootAreaId())),
                    cb.equal(resourceTree.get("ancestorId"), grant.get("resourceId")),
                    cb.equal(resourceTree.get("descendantId"), descriptor.resourceId(root, cb)));

            return cb.exists(grants);
        };
    }

    private void deny(AccessLevel level, String resourceCode) {
        metrics.denied(resourceCode);
        throw new AccessDeniedException("User " + CurrentUser.id() + " lacks " + level + " on " + resourceCode);
    }
}
