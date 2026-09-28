package com.bukcase.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One cell of a profile's permission matrix: access level over an area subtree crossed with a
 * resource subtree. Mapped read-only for list filtering; writes go through the admin service.
 */
@Entity
@Table(name = "profile_grants")
public class ProfileGrant {

    @Id
    private Long id;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "access_level")
    private short accessLevel;

    protected ProfileGrant() {
    }
}
