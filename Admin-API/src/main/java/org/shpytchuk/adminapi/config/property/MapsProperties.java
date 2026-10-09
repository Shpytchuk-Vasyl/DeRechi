package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "derechi.maps")
public record MapsProperties(String apiKey, String region) {

    public MapsProperties {
        region = StringUtils.hasText(region) ? region : "UA";
    }
}
