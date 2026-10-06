package org.shpytchuk.adminapi.config;

import org.shpytchuk.adminapi.config.property.AdminProperties;
import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.NotificationProperties;
import org.shpytchuk.adminapi.security.KeycloakAuthoritiesMapper;
import org.shpytchuk.adminapi.security.PermissionChecker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({AdminProperties.class, NotificationProperties.class, ArchiveProperties.class})
public class SecurityConfig {

    static final String[] PUBLIC_ACTUATOR = {"/actuator/health", "/actuator/health/**", "/actuator/info",
            "/actuator/prometheus"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LogoutSuccessHandler logoutSuccessHandler) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_ACTUATOR).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable))
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
                .build();
    }

    @Bean
    public AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean("perm")
    public PermissionChecker permissionChecker() {
        return new PermissionChecker();
    }

    @Bean
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper(AdminProperties properties) {
        return new KeycloakAuthoritiesMapper(properties.clientId());
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler(ClientRegistrationRepository clientRegistrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrations);
        handler.setPostLogoutRedirectUri("{baseUrl}/admin");
        return handler;
    }
}
