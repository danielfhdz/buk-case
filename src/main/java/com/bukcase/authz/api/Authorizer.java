package com.bukcase.authz.api;

import org.springframework.data.jpa.domain.Specification;

/**
 * Entry point of the authorization engine for module teams. All methods evaluate the user bound
 * to {@link com.bukcase.identity.CurrentUser}.
 */
public interface Authorizer {

    boolean can(AccessLevel level, Object record);

    void check(AccessLevel level, Object record);

    boolean canAccessResource(AccessLevel level, String resourceCode);

    void checkResource(AccessLevel level, String resourceCode);

    /**
     * Filter to compose into any Spring Data query so that only records the user may read are
     * returned. Evaluated entirely in SQL.
     */
    <T> Specification<T> readable(Class<T> entityType);

    <T> Specification<T> writable(Class<T> entityType);
}
