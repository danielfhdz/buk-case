package com.bukcase.authz.engine;

import com.bukcase.authz.cache.AuthzVersion;
import com.bukcase.authz.cache.PermissionCache;
import org.springframework.stereotype.Component;

@Component
public class PermissionProvider {

    private final AuthzVersion version;
    private final PermissionCache cache;
    private final PermissionCompiler compiler;
    private final AuthorizationMetrics metrics;

    public PermissionProvider(AuthzVersion version, PermissionCache cache, PermissionCompiler compiler,
                              AuthorizationMetrics metrics) {
        this.version = version;
        this.cache = cache;
        this.compiler = compiler;
        this.metrics = metrics;
    }

    public CompiledPermissions forUser(long userId) {
        long current = version.current();
        return cache.get(current, userId)
                .map(permissions -> {
                    metrics.cacheHit();
                    return permissions;
                })
                .orElseGet(() -> {
                    metrics.cacheMiss();
                    CompiledPermissions compiled = metrics.timeCompile(() -> compiler.compile(userId));
                    cache.put(current, compiled);
                    return compiled;
                });
    }
}
