package org.shpytchuk.ingest.storage;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Exception;
import io.awspring.cloud.s3.S3Operations;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.config.StorageProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class S3ImageUploaderTest {

    private final S3Operations storage = mock(S3Operations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneId.of("Europe/Kyiv"));
    private final S3ImageUploader uploader = new S3ImageUploader(storage,
            new StorageProperties("derechi-files", DataSize.ofBytes(10)), clock);

    @Test
    void uploadsJpegUnderItemsFolderOfTheUtcMonth() throws IOException {
        byte[] jpeg = {1, 2, 3};

        String key = uploader.upload(jpeg);

        assertThat(key).matches("items/2026/10/[0-9a-f-]{36}\\.jpg");
        ArgumentCaptor<InputStream> content = ArgumentCaptor.forClass(InputStream.class);
        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(storage).upload(eq("derechi-files"), eq(key), content.capture(), metadata.capture());
        assertThat(content.getValue().readAllBytes()).isEqualTo(jpeg);
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void keysAreUnique() {
        assertThat(uploader.upload(new byte[]{1})).isNotEqualTo(uploader.upload(new byte[]{1}));
    }

    @Test
    void rejectsPhotoLargerThanTheLimit() {
        assertThatThrownBy(() -> uploader.upload(new byte[11]))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("exceeds the limit");
        verifyNoInteractions(storage);
    }

    @Test
    void acceptsPhotoOfExactlyTheLimit() {
        assertThat(uploader.upload(new byte[10])).startsWith("items/");
    }

    @Test
    void wrapsUploadFailure() {
        when(storage.upload(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new S3Exception("Failed to upload", new IllegalStateException("bucket is gone")));

        assertThatThrownBy(() -> uploader.upload(new byte[]{1}))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("IllegalStateException: bucket is gone");
    }
}
