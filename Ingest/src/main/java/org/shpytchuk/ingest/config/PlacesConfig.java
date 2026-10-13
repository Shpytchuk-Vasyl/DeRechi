package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.place.PlaceResolver;
import org.shpytchuk.ingest.place.google.GoogleLegacyPlaceResolver;
import org.shpytchuk.ingest.place.google.GooglePlaceResolver;
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
