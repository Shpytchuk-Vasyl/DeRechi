package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.geo.GooglePlaceResolver;
import org.shpytchuk.telegramingest.http.HttpCalls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PlacesConfig {

    @Bean
    public GooglePlaceResolver googlePlaceResolver(PlacesProperties properties) {
        return new GooglePlaceResolver(HttpCalls.restClient(properties.timeout()), properties);
    }
}
