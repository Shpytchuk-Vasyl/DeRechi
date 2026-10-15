package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "derechi.notice-status")
public record NoticeStatusProperties(String exchange) {

    public NoticeStatusProperties {
        Assert.hasText(exchange, "derechi.notice-status.exchange must not be blank");
    }
}
