package org.shpytchuk.clientapi.config;

import org.shpytchuk.clientapi.client.FourthwallClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class FourthwallConfig {

    @Bean
    public FourthwallClient fourthwallClient(FourthwallProperties properties) {
        return FourthwallClient.of(RestClient.builder(), properties);
    }
}
