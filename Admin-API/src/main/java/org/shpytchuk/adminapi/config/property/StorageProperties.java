package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "derechi.storage")
public record StorageProperties(String bucket, String publicUrl, DataSize maxSize) {

    public StorageProperties {
        maxSize = maxSize == null ? DataSize.ofMegabytes(5) : maxSize;
        publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    public String urlOf(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        // publicUrl already points at the bucket: MinIO's /<bucket> directly, or the Gateway's /files,
        // which rewrites to /<bucket> itself
        return "%s/%s".formatted(publicUrl, key.replaceAll("^/+", ""));
    }
}
