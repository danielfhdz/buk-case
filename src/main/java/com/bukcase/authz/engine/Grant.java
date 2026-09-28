package com.bukcase.authz.engine;

import com.bukcase.authz.api.AccessLevel;

public record Grant(long areaId, long resourceId, AccessLevel level) {
}
