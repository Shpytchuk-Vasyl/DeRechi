package org.shpytchuk.adminapi.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Operations;
import org.shpytchuk.adminapi.config.property.StorageProperties;
import org.shpytchuk.adminapi.exception.RejectedUploadException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageStorage {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final S3Operations storage;
    private final StorageProperties properties;

    public ImageStorage(S3Operations storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public String store(MultipartFile file) {
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);

        if (extension == null) {
            throw new RejectedUploadException("upload.unsupportedType");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new RejectedUploadException("upload.tooLarge");
        }

        String key = "items/%s/%s.%s".formatted(LocalDate.now().format(FOLDER), UUID.randomUUID(), extension);

        try (InputStream content = file.getInputStream()) {
            storage.upload(properties.bucket(), key, content,
                    ObjectMetadata.builder().contentType(contentType).build());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return key;
    }

    public void delete(String key) {
        storage.deleteObject(properties.bucket(), key);
    }

    public String urlOf(String key) {
        return properties.urlOf(key);
    }

    public long getMaxBytes() {
        return properties.maxSize().toBytes();
    }
}
