package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.maps")
public record MapsProperties(String apiKey, String region) {

    public MapsProperties {
        region = region == null || region.isBlank() ? "UA" : region;
    }
}
