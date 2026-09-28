package com.bukcase.authz.cache;

import com.bukcase.authz.engine.CompiledPermissions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PermissionCache {

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String prefix;

    public PermissionCache(StringRedisTemplate redis, ObjectMapper json,
                           @Value("${authz.cache.namespace}") String namespace) {
        this.redis = redis;
        this.json = json;
        this.prefix = "authz:" + namespace + ":permissions:";
    }

    public Optional<CompiledPermissions> get(long version, long userId) {
        String value = redis.opsForValue().get(key(version, userId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(value, CompiledPermissions.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void put(long version, CompiledPermissions permissions) {
        try {
            redis.opsForValue().set(key(version, permissions.userId()), json.writeValueAsString(permissions), TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize compiled permissions", e);
        }
    }

    private String key(long version, long userId) {
        return prefix + "v" + version + ":user:" + userId;
    }
}
