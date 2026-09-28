package com.bukcase.authz.api;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/**
 * The only integration a module team writes: tells the engine how an entity reaches its
 * organizational area (X axis) and its resource node (Y axis).
 *
 * <p>Both a query form (for list filtering in SQL) and an instance form (for point checks in
 * memory) are required; they must describe the same mapping. Query expressions are used only in
 * the outer query, so joins through nullable associations must be explicit LEFT joins.
 *
 * @param <T> the protected entity
 */
public interface AuthorizationDescriptor<T> {

    Class<T> entityType();

    /**
     * Code of the module's root resource node. Grants outside this module are ignored when
     * filtering lists of this entity.
     */
    String module();

    /**
     * Area the record belongs to, or a null-valued expression when it has none. Records without an
     * area are only visible through company-wide grants.
     */
    Expression<Long> areaId(Root<T> root, CriteriaBuilder cb);

    Expression<Long> resourceId(Root<T> root, CriteriaBuilder cb);

    Long areaIdOf(T entity);

    long resourceIdOf(T entity);
}
