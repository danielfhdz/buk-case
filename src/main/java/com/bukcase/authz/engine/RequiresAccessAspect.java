package com.bukcase.authz.engine;

import com.bukcase.authz.api.Authorizer;
import com.bukcase.authz.api.RequiresAccess;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequiresAccessAspect {

    private final Authorizer authorizer;

    public RequiresAccessAspect(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Before(value = "@annotation(requiresAccess)", argNames = "requiresAccess")
    public void enforce(RequiresAccess requiresAccess) {
        authorizer.checkResource(requiresAccess.level(), requiresAccess.resource());
    }
}
