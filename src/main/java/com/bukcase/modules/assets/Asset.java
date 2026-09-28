package com.bukcase.modules.assets;

import com.bukcase.org.Employee;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private AssetCategory category;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /**
     * Area of the assigned employee, derived in SQL so authorization filters can use it as a plain
     * column instead of navigating a nullable association. Null when the asset is unassigned.
     */
    @Formula("(SELECT p.area_id FROM employees e JOIN positions p ON p.id = e.position_id WHERE e.id = employee_id)")
    private Long areaId;

    protected Asset() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Long getAreaId() {
        return areaId;
    }
}
