package com.bukcase.authz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "user_profiles")
@IdClass(UserProfile.Key.class)
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    protected UserProfile() {
    }

    public static class Key implements Serializable {

        private Long userId;
        private Long profileId;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(userId, other.userId)
                    && Objects.equals(profileId, other.profileId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, profileId);
        }
    }
}
