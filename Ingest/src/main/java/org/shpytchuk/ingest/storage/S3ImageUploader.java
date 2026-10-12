package org.shpytchuk.ingest.storage;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Operations;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.ingest.config.StorageProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Service
@AllArgsConstructor
@Slf4j
public class S3ImageUploader implements ImageUploader {

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final S3Operations storage;
    private final StorageProperties properties;
    private final Clock clock;

    @Override
    public String upload(byte[] jpeg) {
        long limit = properties.maxSize().toBytes();
        if (jpeg.length > limit) {
            throw new IngestException("Photo of %d bytes exceeds the limit of %d".formatted(jpeg.length, limit));
        }
        String key = "items/%s/%s.jpg".formatted(FOLDER.format(clock.instant()), UUID.randomUUID());
        try {
            storage.upload(properties.bucket(), key, new ByteArrayInputStream(jpeg),
                    ObjectMetadata.builder().contentType(MediaType.IMAGE_JPEG_VALUE).contentLength((long) jpeg.length).build());
        } catch (RuntimeException e) {
            throw new IngestException("Upload of photo %s failed: %s".formatted(key, getMostSpecificCause(e).toString()));
        }
        log.debug("Uploaded photo {} ({} bytes)", key, jpeg.length);
        return key;
    }
}
