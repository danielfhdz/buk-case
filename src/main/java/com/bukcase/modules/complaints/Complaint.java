package com.bukcase.modules.complaints;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "type_id")
    private ComplaintType type;

    @Column(name = "area_id")
    private Long areaId;

    protected Complaint() {
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintType getType() {
        return type;
    }

    public Long getAreaId() {
        return areaId;
    }
}
