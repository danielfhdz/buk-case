package com.bukcase.authz.engine;

import com.bukcase.authz.api.AccessLevel;
import java.util.List;
import java.util.Set;

/**
 * Effective permissions of one user: the union of the grants of all their profiles, flattened
 * so a point check is a pure in-memory lookup.
 */
public record CompiledPermissions(long userId, boolean admin, List<Grant> grants) {

    public boolean covers(AccessLevel required, Set<Long> areaPath, Set<Long> resourcePath) {
        if (admin) {
            return true;
        }
        for (Grant grant : grants) {
            if (grant.level().satisfies(required)
                    && areaPath.contains(grant.areaId())
                    && resourcePath.contains(grant.resourceId())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAny(AccessLevel required) {
        return admin || grants.stream().anyMatch(grant -> grant.level().satisfies(required));
    }
}
