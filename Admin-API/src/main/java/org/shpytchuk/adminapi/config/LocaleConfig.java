package org.shpytchuk.adminapi.config;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    public static final List<Locale> SUPPORTED = List.of(
            Locale.of("uk", "UA"), Locale.of("en", "GB"), Locale.of("pl", "PL"),
            Locale.of("de", "DE"), Locale.of("fr", "FR"));

    public static final String LANGUAGE_PARAM = "lang";

    public static Locale current() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return SUPPORTED.stream()
                .filter(supported -> supported.getLanguage().equals(language))
                .findFirst()
                .orElse(SUPPORTED.getFirst());
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("DERECHI_LOCALE");
        resolver.setDefaultLocale(SUPPORTED.getFirst());
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(LANGUAGE_PARAM);
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
