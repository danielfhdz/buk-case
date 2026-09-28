package com.bukcase.authz.engine;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationMetrics {

    private final MeterRegistry registry;
    private final Timer checkTimer;
    private final Timer compileTimer;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public AuthorizationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.checkTimer = Timer.builder("authz.check")
                .description("Point permission check latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.compileTimer = Timer.builder("authz.compile")
                .description("Time to compile a user's effective permissions on cache miss")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.cacheHits = Counter.builder("authz.cache").tag("result", "hit").register(registry);
        this.cacheMisses = Counter.builder("authz.cache").tag("result", "miss").register(registry);
    }

    public <T> T timeCheck(Supplier<T> check) {
        return checkTimer.record(check);
    }

    public <T> T timeCompile(Supplier<T> compile) {
        return compileTimer.record(compile);
    }

    public void cacheHit() {
        cacheHits.increment();
    }

    public void cacheMiss() {
        cacheMisses.increment();
    }

    public void denied(String resourceCode) {
        registry.counter("authz.denied", "resource", resourceCode).increment();
    }
}
