package com.bukcase.modules.complaints;

import com.bukcase.authz.api.AuthorizationDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

@Component
class ComplaintAuthorization implements AuthorizationDescriptor<Complaint> {

    @Override
    public Class<Complaint> entityType() {
        return Complaint.class;
    }

    @Override
    public String module() {
        return "COMPLAINTS";
    }

    @Override
    public Expression<Long> areaId(Root<Complaint> root, CriteriaBuilder cb) {
        return root.<Long>get("areaId");
    }

    @Override
    public Expression<Long> resourceId(Root<Complaint> root, CriteriaBuilder cb) {
        return root.get("type").<Long>get("resourceId");
    }

    @Override
    public Long areaIdOf(Complaint complaint) {
        return complaint.getAreaId();
    }

    @Override
    public long resourceIdOf(Complaint complaint) {
        return complaint.getType().getResourceId();
    }
}
