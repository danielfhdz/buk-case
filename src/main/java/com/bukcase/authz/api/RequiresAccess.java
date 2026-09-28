package com.bukcase.authz.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Coarse, declarative gate for operations that are not about a single record: the current user
 * must hold at least {@link #level()} on the resource or on any node below it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAccess {

    String resource();

    AccessLevel level() default AccessLevel.READ;
}
