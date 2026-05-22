package com.nageoffer.ai.ragent.core.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreventDuplicateSubmit {

    long ttlSeconds() default 5L;

    String keyPrefix() default "ragent:duplicate-submit";

    boolean releaseOnCompletion() default false;
}
