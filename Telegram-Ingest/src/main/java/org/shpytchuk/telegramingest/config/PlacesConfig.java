package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.geo.GoogleLegacyPlaceResolver;
import org.shpytchuk.telegramingest.geo.GooglePlaceResolver;
import org.shpytchuk.telegramingest.geo.PlaceResolver;
import org.shpytchuk.telegramingest.http.HttpCalls;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlacesConfig {

    @Bean
    @ConditionalOnProperty(prefix = "derechi.places", name = "api", havingValue = "NEW")
    public PlaceResolver googlePlaceResolver(PlacesProperties properties) {
        return new GooglePlaceResolver(HttpCalls.restClient(properties.timeout()), properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "derechi.places", name = "api", havingValue = "LEGACY", matchIfMissing = true)
    public PlaceResolver googleLegacyPlaceResolver(PlacesProperties properties) {
        return new GoogleLegacyPlaceResolver(HttpCalls.restClient(properties.timeout()), properties);
    }
}
