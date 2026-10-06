package org.shpytchuk.clientapi.config;

import org.shpytchuk.clientapi.client.FourthwallClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class FourthwallConfig {

    @Bean
    public FourthwallClient fourthwallClient(FourthwallProperties properties) {
        return FourthwallClient.of(RestClient.builder().requestFactory(requestFactory(properties)), properties);
    }

    static JdkClientHttpRequestFactory requestFactory(FourthwallProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
