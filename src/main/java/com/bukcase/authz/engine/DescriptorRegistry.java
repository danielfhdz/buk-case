package com.bukcase.authz.engine;

import com.bukcase.authz.api.AuthorizationDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DescriptorRegistry {

    private final Map<Class<?>, AuthorizationDescriptor<?>> byType = new HashMap<>();

    public DescriptorRegistry(List<AuthorizationDescriptor<?>> descriptors) {
        descriptors.forEach(descriptor -> byType.put(descriptor.entityType(), descriptor));
    }

    @SuppressWarnings("unchecked")
    public <T> AuthorizationDescriptor<T> forType(Class<T> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            AuthorizationDescriptor<?> descriptor = byType.get(current);
            if (descriptor != null) {
                return (AuthorizationDescriptor<T>) descriptor;
            }
        }
        throw new IllegalArgumentException("No AuthorizationDescriptor registered for " + type.getName());
    }

    @SuppressWarnings("unchecked")
    public AuthorizationDescriptor<Object> forRecord(Object record) {
        return (AuthorizationDescriptor<Object>) forType(record.getClass());
    }
}
