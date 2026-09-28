package com.bukcase.authz.api;

public enum AccessLevel {
    READ((short) 1),
    WRITE((short) 2);

    private final short code;

    AccessLevel(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public boolean satisfies(AccessLevel required) {
        return code >= required.code;
    }

    public static AccessLevel fromCode(int code) {
        for (AccessLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown access level code: " + code);
    }
}
