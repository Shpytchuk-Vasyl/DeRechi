package org.shpytchuk.adminapi.config;

import org.shpytchuk.adminapi.config.property.MapsProperties;
import org.shpytchuk.adminapi.view.GoogleMaps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MapsProperties.class)
public class MapsConfig {

    @Bean
    public GoogleMaps googleMaps(MapsProperties properties) {
        return new GoogleMaps(properties);
    }
}
