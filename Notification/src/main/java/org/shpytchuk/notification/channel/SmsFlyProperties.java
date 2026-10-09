package org.shpytchuk.notification.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

@ConfigurationProperties(prefix = "derechi.sms-fly")
public record SmsFlyProperties(
        String apiUrl,
        String apiKey,
        String source,
        Duration ttl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Duration MAX_TTL = Duration.ofHours(24);

    public SmsFlyProperties {
        apiUrl = require(apiUrl, "api-url");
        apiKey = require(apiKey, "api-key");
        source = require(source, "source");
        if (ttl == null || ttl.toMinutes() < 1 || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("derechi.sms-fly.ttl must be between 1m and 24h: " + ttl);
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readTimeout, "read-timeout");
    }

    public int ttlMinutes() {
        return Math.toIntExact(ttl.toMinutes());
    }

    @Override
    public String toString() {
        return "SmsFlyProperties[apiUrl=" + apiUrl + ", apiKey=***, source=" + source + ", ttl=" + ttl
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }

    private static String require(String value, String name) {
        Assert.hasText(value, "derechi.sms-fly." + name + " must be set");
        return value.strip();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || !value.isPositive()) {
            throw new IllegalArgumentException("derechi.sms-fly." + name + " must be positive: " + value);
        }
    }
}
