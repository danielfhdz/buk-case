package com.bukcase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context against the PostgreSQL and Redis from {@code compose.yaml}, which are
 * started automatically if they are not running.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"spring.docker.compose.skip.in-tests=false", "demo.console.enabled=false"})
public @interface IntegrationTest {
}
