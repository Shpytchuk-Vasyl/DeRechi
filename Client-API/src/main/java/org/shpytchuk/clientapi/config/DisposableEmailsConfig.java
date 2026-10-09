package org.shpytchuk.clientapi.config;

import org.shpytchuk.clientapi.validator.DisposableEmailDomains;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class DisposableEmailsConfig {

    @Bean
    public DisposableEmailDomains disposableEmailDomains(DisposableEmailsProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build());
        factory.setReadTimeout(properties.timeout());
        return new DisposableEmailDomains(RestClient.builder().requestFactory(factory).build(), properties.url());
    }
}
