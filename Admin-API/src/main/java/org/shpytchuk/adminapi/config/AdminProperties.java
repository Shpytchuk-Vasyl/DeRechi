package org.shpytchuk.adminapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.admin")
public record AdminProperties(String clientId, int pageSize) {

    public AdminProperties {
        if (pageSize < 1) {
            pageSize = 20;
        }
    }
}
