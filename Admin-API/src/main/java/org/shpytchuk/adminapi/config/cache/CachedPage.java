package org.shpytchuk.adminapi.config.cache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GET requests handled by this controller can be stored in the browser's private cache for the specified duration.
 * Any other method (POST/PUT/PATCH/DELETE) is served with {@code no-store},
 * and a subsequent redirect clears the cached list – see {@link CacheControlInterceptor}.
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CachedPage {

    int seconds() default 60;
}
