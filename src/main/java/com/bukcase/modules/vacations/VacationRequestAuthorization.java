package com.bukcase.modules.vacations;

import com.bukcase.authz.api.AuthorizationDescriptor;
import com.bukcase.authz.api.ResourceCatalog;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

/**
 * Vacation requests have no sub-types: all of them map to the module node itself, and belong to
 * the area of the requesting employee.
 */
@Component
class VacationRequestAuthorization implements AuthorizationDescriptor<VacationRequest> {

    private static final String RESOURCE = "VACATIONS";

    private final ResourceCatalog resources;

    VacationRequestAuthorization(ResourceCatalog resources) {
        this.resources = resources;
    }

    @Override
    public Class<VacationRequest> entityType() {
        return VacationRequest.class;
    }

    @Override
    public Expression<Long> areaId(Root<VacationRequest> root, CriteriaBuilder cb) {
        return root.join("employee").join("position").<Long>get("areaId");
    }

    @Override
    public Expression<Long> resourceId(Root<VacationRequest> root, CriteriaBuilder cb) {
        return cb.literal(resources.idOf(RESOURCE));
    }

    @Override
    public Long areaIdOf(VacationRequest request) {
        return request.getEmployee().getAreaId();
    }

    @Override
    public long resourceIdOf(VacationRequest request) {
        return resources.idOf(RESOURCE);
    }
}
