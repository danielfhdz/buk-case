package com.bukcase.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    private Long id;

    private String name;

    @Column(name = "area_id")
    private Long areaId;

    protected Position() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getAreaId() {
        return areaId;
    }
}
