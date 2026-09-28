package com.bukcase.modules.assets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_categories")
public class AssetCategory {

    @Id
    private Long id;

    private String name;

    @Column(name = "resource_id")
    private Long resourceId;

    protected AssetCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getResourceId() {
        return resourceId;
    }
}
