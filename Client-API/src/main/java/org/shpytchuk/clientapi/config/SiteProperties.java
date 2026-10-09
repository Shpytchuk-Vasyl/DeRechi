package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "derechi.site")
public record SiteProperties(String url) {

    public SiteProperties {
        Assert.hasText(url, "derechi.site.url must be set");
        url = url.strip();
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String notice(String kind, Long itemId) {
        return url + "/" + kind + "/" + itemId;
    }
}
