package com.bukcase.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "position_id")
    private Position position;

    protected Employee() {
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Position getPosition() {
        return position;
    }

    public Long getAreaId() {
        return position.getAreaId();
    }
}
