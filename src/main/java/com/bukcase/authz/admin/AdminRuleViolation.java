package com.bukcase.authz.admin;

public class AdminRuleViolation extends RuntimeException {

    public AdminRuleViolation(String message) {
        super(message);
    }
}
