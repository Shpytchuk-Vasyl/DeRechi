package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "derechi.site")
public record SiteProperties(String url) {

    public SiteProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("derechi.site.url must be set");
        }
        url = url.strip();
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String notice(String kind, Long itemId) {
        return url + "/" + kind + "/" + itemId;
    }
}
