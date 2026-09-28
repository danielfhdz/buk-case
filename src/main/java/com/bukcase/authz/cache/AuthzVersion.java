package com.bukcase.authz.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tenant-wide version of the authorization configuration. Any change to profiles, grants,
 * assignments or trees bumps it, which makes every cached entry keyed by the old version
 * unreachable.
 *
 * <p>Each instance re-reads the shared value at most every {@code authz.version-refresh-ms}
 * milliseconds. That interval is the upper bound for a change to be observed by other instances,
 * traded for not paying a Redis round trip on every single check.
 */
@Component
public class AuthzVersion {

    private final StringRedisTemplate redis;
    private final String key;
    private final long refreshMillis;

    private volatile long cachedVersion;
    private volatile long readAtMillis;

    public AuthzVersion(StringRedisTemplate redis,
                        @Value("${authz.cache.namespace}") String namespace,
                        @Value("${authz.version-refresh-ms}") long refreshMillis) {
        this.redis = redis;
        this.key = "authz:" + namespace + ":version";
        this.refreshMillis = refreshMillis;
        this.readAtMillis = -refreshMillis;
    }

    public long current() {
        long now = System.currentTimeMillis();
        if (now - readAtMillis >= refreshMillis) {
            String value = redis.opsForValue().get(key);
            cachedVersion = value == null ? 0L : Long.parseLong(value);
            readAtMillis = now;
        }
        return cachedVersion;
    }

    public long bump() {
        Long next = redis.opsForValue().increment(key);
        cachedVersion = next == null ? cachedVersion + 1 : next;
        readAtMillis = System.currentTimeMillis();
        return cachedVersion;
    }
}
