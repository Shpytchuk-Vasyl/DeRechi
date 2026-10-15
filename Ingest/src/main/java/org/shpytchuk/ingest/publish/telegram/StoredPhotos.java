package org.shpytchuk.ingest.publish.telegram;

import io.awspring.cloud.s3.S3Operations;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.storage.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Component
@AllArgsConstructor
@Slf4j
public class StoredPhotos {

    private static final List<String> PHOTO_EXTENSIONS = List.of(".jpg", ".jpeg", ".png");

    private final S3Operations storage;
    private final StorageProperties properties;

    public byte @Nullable [] load(@Nullable String key) {
        if (key == null || PHOTO_EXTENSIONS.stream().noneMatch(key.toLowerCase(Locale.ROOT)::endsWith)) {
            return null;
        }
        long limit = properties.maxSize().toBytes();
        try (InputStream photo = storage.download(properties.bucket(), key).getInputStream()) {
            byte[] bytes = photo.readNBytes(Math.toIntExact(limit + 1));
            if (bytes.length > limit) {
                log.warn("Photo {} is over {} bytes, the post goes without it", key, limit);
                return null;
            }
            return bytes;
        } catch (IOException | RuntimeException e) {
            log.warn("Photo {} was not read, the post goes without it: {}", key, getMostSpecificCause(e).toString());
            return null;
        }
    }
}
