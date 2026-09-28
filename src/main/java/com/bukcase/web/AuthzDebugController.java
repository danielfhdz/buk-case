package com.bukcase.web;

import com.bukcase.authz.engine.CompiledPermissions;
import com.bukcase.authz.engine.PermissionProvider;
import com.bukcase.identity.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only view of the current user's compiled permissions.
 */
@RestController
class AuthzDebugController {

    private final PermissionProvider permissions;

    AuthzDebugController(PermissionProvider permissions) {
        this.permissions = permissions;
    }

    @GetMapping("/authz/me")
    CompiledPermissions me() {
        return permissions.forUser(CurrentUser.id());
    }
}
