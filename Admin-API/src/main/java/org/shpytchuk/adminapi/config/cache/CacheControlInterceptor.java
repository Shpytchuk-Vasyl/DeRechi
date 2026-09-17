package org.shpytchuk.adminapi.config.cache;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;

public class CacheControlInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, @Nullable ModelAndView modelAndView) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !HttpMethod.GET.matches(request.getMethod())
                || !HttpStatus.valueOf(response.getStatus()).is2xxSuccessful()) {
            return;
        }

        CachedPage cached = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), CachedPage.class);
        if (cached != null) {
            response.setHeader(HttpHeaders.CACHE_CONTROL,
                    CacheControl.maxAge(Duration.ofSeconds(cached.seconds())).cachePrivate().getHeaderValue());
        }
    }
}
