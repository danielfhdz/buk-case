package com.bukcase.modules.assets;

import com.bukcase.authz.api.AuthorizationDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

/**
 * An asset belongs to the area of the employee it is assigned to (none when unassigned) and to
 * the resource node of its category. The association to the employee is nullable, so the query
 * form uses explicit LEFT joins.
 */
@Component
class AssetAuthorization implements AuthorizationDescriptor<Asset> {

    @Override
    public Class<Asset> entityType() {
        return Asset.class;
    }

    @Override
    public String module() {
        return "ASSETS";
    }

    @Override
    public Expression<Long> areaId(Root<Asset> root, CriteriaBuilder cb) {
        return root.join("employee", JoinType.LEFT).join("position", JoinType.LEFT).<Long>get("areaId");
    }

    @Override
    public Expression<Long> resourceId(Root<Asset> root, CriteriaBuilder cb) {
        return root.get("category").<Long>get("resourceId");
    }

    @Override
    public Long areaIdOf(Asset asset) {
        return asset.getAreaId();
    }

    @Override
    public long resourceIdOf(Asset asset) {
        return asset.getCategory().getResourceId();
    }
}
