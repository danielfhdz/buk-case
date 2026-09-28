package com.bukcase.modules.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaint_types")
public class ComplaintType {

    @Id
    private Long id;

    private String name;

    @Column(name = "resource_id")
    private Long resourceId;

    protected ComplaintType() {
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
