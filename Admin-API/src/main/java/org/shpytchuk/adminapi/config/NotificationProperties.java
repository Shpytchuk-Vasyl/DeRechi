package org.shpytchuk.adminapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.notifications")
public record NotificationProperties(String exchange, String routingKey) {
}
