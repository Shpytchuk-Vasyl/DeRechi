package org.shpytchuk.notification.config;

import org.shpytchuk.notification.channel.SmsFlyChannel;
import org.shpytchuk.notification.channel.SmsFlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@Conditional(SmsFlyConfig.ApiKeyPresent.class)
@EnableConfigurationProperties(SmsFlyProperties.class)
public class SmsFlyConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsFlyConfig.class);

    static final String API_KEY = "derechi.sms-fly.api-key";

    @Bean
    public SmsFlyChannel smsFlyChannel(SmsFlyProperties properties) {
        log.info("SMS channel is on: SMS-fly {} as {}", properties.apiUrl(), properties.source());
        return SmsFlyChannel.of(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    static JdkClientHttpRequestFactory requestFactory(SmsFlyProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    static class ApiKeyPresent implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty(API_KEY));
        }
    }
}
