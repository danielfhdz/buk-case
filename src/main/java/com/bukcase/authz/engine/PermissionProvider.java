package com.bukcase.authz.engine;

import com.bukcase.authz.cache.AuthzVersion;
import com.bukcase.authz.cache.PermissionCache;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Two cache levels in front of the compiler: a small in-process LRU (no network, no parsing) and
 * the shared Redis cache. Both are keyed by the tenant version, so a version bump invalidates
 * them together; the in-process level adds no staleness beyond the version refresh interval.
 */
@Component
public class PermissionProvider {

    private static final int LOCAL_CAPACITY = 10_000;

    private final AuthzVersion version;
    private final PermissionCache cache;
    private final PermissionCompiler compiler;
    private final AuthorizationMetrics metrics;
    private final Map<String, CompiledPermissions> local = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompiledPermissions> eldest) {
                    return size() > LOCAL_CAPACITY;
                }
            });

    public PermissionProvider(AuthzVersion version, PermissionCache cache, PermissionCompiler compiler,
                              AuthorizationMetrics metrics) {
        this.version = version;
        this.cache = cache;
        this.compiler = compiler;
        this.metrics = metrics;
    }

    public CompiledPermissions forUser(long userId) {
        long current = version.current();
        String localKey = current + ":" + userId;

        CompiledPermissions inProcess = local.get(localKey);
        if (inProcess != null) {
            metrics.cacheHit("local");
            return inProcess;
        }

        Optional<CompiledPermissions> shared = cache.get(current, userId);
        CompiledPermissions permissions;
        if (shared.isPresent()) {
            metrics.cacheHit("redis");
            permissions = shared.get();
        } else {
            metrics.cacheMiss();
            permissions = metrics.timeCompile(() -> compiler.compile(userId));
            cache.put(current, permissions);
        }
        local.put(localKey, permissions);
        return permissions;
    }
}
