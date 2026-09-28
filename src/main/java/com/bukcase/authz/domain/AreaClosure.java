package com.bukcase.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "area_closure")
@IdClass(AreaClosure.Key.class)
public class AreaClosure {

    @Id
    @Column(name = "ancestor_id")
    private Long ancestorId;

    @Id
    @Column(name = "descendant_id")
    private Long descendantId;

    private int depth;

    protected AreaClosure() {
    }

    public static class Key implements Serializable {

        private Long ancestorId;
        private Long descendantId;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(ancestorId, other.ancestorId)
                    && Objects.equals(descendantId, other.descendantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ancestorId, descendantId);
        }
    }
}
