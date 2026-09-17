package org.shpytchuk.adminapi.config;

import org.shpytchuk.adminapi.config.cache.CacheControlInterceptor;
import org.shpytchuk.adminapi.config.property.AdminProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("id"));

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/admin");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CacheControlInterceptor());
    }

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer adminPageableDefaults(AdminProperties properties) {
        return resolver -> {
            resolver.setFallbackPageable(PageRequest.of(0, properties.pageSize(), NEWEST_FIRST));
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
        };
    }
}
