package org.shpytchuk.ingest.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "derechi.storage")
public record StorageProperties(String bucket, DataSize maxSize) {

    public StorageProperties {
        bucket = StringUtils.hasText(bucket) ? bucket.trim() : "derechi-files";
        maxSize = maxSize == null ? DataSize.ofMegabytes(5) : maxSize;
    }
}
