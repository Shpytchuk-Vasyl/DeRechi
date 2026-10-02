package org.shpytchuk.launcher;

import org.shpytchuk.adminapi.AdminApiApplication;
import org.shpytchuk.worker.WorkerApplication;
import org.shpytchuk.clientapi.ClientApiApplication;
//import org.shpytchuk.discovery.DiscoveryApplication;
import org.shpytchuk.getaway.GetawayApplication;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DeRechiLauncher {

    private static final String DISCOVERY = "Discovery";
    private static final String LOAD_BALANCER_SCHEME = "lb://";

    private static final List<String> SECURITY = List.of(
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.reactive.ReactiveManagementWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientWebSecurityAutoConfiguration");

    private static final List<String> GRAPHQL = List.of(
            "org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.observation.GraphQlObservationAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.servlet.GraphQlWebMvcAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.reactive.GraphQlWebFluxAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebMvcSecurityAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.security.GraphQlWebFluxSecurityAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.data.GraphQlQueryByExampleAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.data.GraphQlQuerydslAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.data.GraphQlReactiveQueryByExampleAutoConfiguration",
            "org.springframework.boot.graphql.autoconfigure.data.GraphQlReactiveQuerydslAutoConfiguration");

    private static final List<String> PERSISTENCE = List.of(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.metrics.HibernateMetricsAutoConfiguration",
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseEndpointAutoConfiguration");

    private static final List<String> GATEWAY = List.of(
            "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayFunctionAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayStreamAutoConfiguration",
            "org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration",
            "org.springframework.cloud.gateway.config.SimpleUrlHandlerMappingGlobalCorsAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayReactiveLoadBalancerClientAutoConfiguration",
            "org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration",
            "org.springframework.cloud.gateway.config.GatewayTracingAutoConfiguration");

    private static final List<String> AMQP = List.of(
            "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "org.springframework.boot.amqp.autoconfigure.health.RabbitHealthContributorAutoConfiguration",
            "org.springframework.boot.amqp.autoconfigure.metrics.RabbitMetricsAutoConfiguration");

    private static final List<Service> SERVICES = List.of(
//            new Service(DISCOVERY, DiscoveryApplication.class, WebApplicationType.SERVLET,
//                    excludes(SECURITY, GRAPHQL, PERSISTENCE, AMQP, GATEWAY)),
            new Service("Getaway", GetawayApplication.class, WebApplicationType.REACTIVE,
                    excludes(SECURITY, GRAPHQL, PERSISTENCE, AMQP)),
            new Service("Client-API", ClientApiApplication.class, WebApplicationType.SERVLET,
                    excludes(SECURITY, GATEWAY)),
            new Service("Admin-API", AdminApiApplication.class, WebApplicationType.SERVLET,
                    excludes(GRAPHQL, GATEWAY)),
            new Service("Worker", WorkerApplication.class, WebApplicationType.SERVLET,
                    excludes(SECURITY, GRAPHQL, GATEWAY)));

    private DeRechiLauncher() {
    }

    public static void main(String[] args) {
        Map<String, PropertySource<?>> configs;
        try {
            configs = loadConfigs();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<ConfigurableApplicationContext> started = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(started), "derechi-launcher-shutdown"));

        for (Service service : SERVICES) {
            long begin = System.currentTimeMillis();
            System.out.printf("%n=== [launcher] запускаю %s ===%n", service.name());
            try {
                started.add(start(service, configs.get(service.name()), args));
            } catch (Exception e) {
                System.err.printf("[launcher] %s не піднявся%n", service.name());
                e.printStackTrace();
                stop(started);
                System.exit(1);
            }
            System.out.printf("=== [launcher] %s готовий за %.1f с ===%n",
                    service.name(), (System.currentTimeMillis() - begin) / 1000.0);
        }

        System.out.println("""

                [launcher] JVM:
                  Getaway          http://localhost:8080
                  Discovery        http://localhost:8761
                  Client-API       http://localhost:8082 (graphiql: /graphiql)
                  Admin-API        http://localhost:8083/admin
                  Worker http://localhost:8085
                """);
    }

    private static ConfigurableApplicationContext start(Service service, PropertySource<?> config, String[] args) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.config.location", "optional:classpath:derechi-launcher-no-config/");
        defaults.put("spring.main.web-application-type", service.type().name().toLowerCase(Locale.ROOT));
        defaults.put("spring.autoconfigure.exclude", String.join(",", service.excludes()));
        if (!DISCOVERY.equals(service.name())) {
            defaults.put("eureka.client.enabled", "false");
        }

        return new SpringApplicationBuilder(service.mainClass())
                .web(service.type())
                .bannerMode(Banner.Mode.OFF)
                .registerShutdownHook(false)
                .properties(defaults)
                .listeners(new ConfigInjector(config))
                .run(args);
    }

    private static void stop(List<ConfigurableApplicationContext> started) {
        for (int i = started.size() - 1; i >= 0; i--) {
            ConfigurableApplicationContext context = started.get(i);
            if (context.isActive()) {
                context.close();
            }
        }
    }

    private static Map<String, PropertySource<?>> loadConfigs() throws IOException {
        Map<String, PropertySource<?>> configs = new LinkedHashMap<>();
        for (Service service : SERVICES) {
            configs.put(service.name(), configOf(service.name()));
        }

        Map<String, String> localUrls = new LinkedHashMap<>();
        configs.forEach((name, config) ->
                localUrls.put(name.toUpperCase(Locale.ROOT), "http://localhost:" + config.getProperty("server.port")));

        configs.replaceAll((name, config) -> withoutLoadBalancerUris(config, localUrls));
        return configs;
    }

    private static PropertySource<?> withoutLoadBalancerUris(PropertySource<?> config, Map<String, String> localUrls) {
        if (!(config instanceof EnumerablePropertySource<?> enumerable)) {
            return config;
        }

        Map<String, Object> rewritten = new LinkedHashMap<>();
        for (String name : enumerable.getPropertyNames()) {
            Object value = enumerable.getProperty(name);
            if (value instanceof String text && text.startsWith(LOAD_BALANCER_SCHEME)) {
                String rest = text.substring(LOAD_BALANCER_SCHEME.length());
                int pathAt = rest.indexOf('/');
                String serviceId = (pathAt < 0 ? rest : rest.substring(0, pathAt)).toUpperCase(Locale.ROOT);
                String localUrl = localUrls.get(serviceId);
                if (localUrl == null) {
                    throw new IllegalStateException(name + "=" + text + " вказує на сервіс поза лаунчером");
                }
                value = pathAt < 0 ? localUrl : localUrl + rest.substring(pathAt);
                System.out.printf("[launcher] %s: %s -> %s%n", enumerable.getName(), text, value);
            }
            rewritten.put(name, value);
        }
        return new MapPropertySource(enumerable.getName(), rewritten);
    }

    private static PropertySource<?> configOf(String applicationName) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        ClassLoader classLoader = DeRechiLauncher.class.getClassLoader();

        for (String fileName : List.of("application.yaml", "application.yml")) {
            for (URL url : Collections.list(classLoader.getResources(fileName))) {
                Resource resource = new UrlResource(url);
                for (PropertySource<?> source : loader.load("derechi-" + applicationName, resource)) {
                    if (applicationName.equals(source.getProperty("spring.application.name"))) {
                        return source;
                    }
                }
            }
        }
        throw new IllegalStateException("На класпасі немає application.yaml зі spring.application.name="
                + applicationName + " — модуль не зібраний?");
    }

    @SafeVarargs
    private static List<String> excludes(List<String>... groups) {
        List<String> all = new ArrayList<>();
        for (List<String> group : groups) {
            all.addAll(group);
        }
        return List.copyOf(all);
    }

    private record Service(String name, Class<?> mainClass, WebApplicationType type, List<String> excludes) {
    }

    private record ConfigInjector(PropertySource<?> config)
            implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

        @Override
        public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
            event.getEnvironment().getPropertySources().addLast(config);
        }
    }
}
