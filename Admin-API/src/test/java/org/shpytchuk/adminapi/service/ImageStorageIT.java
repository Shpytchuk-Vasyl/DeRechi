package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.shpytchuk.adminapi.config.property.StorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIf("minioIsUp")
class ImageStorageIT {

    private static final String HEALTH = "http://localhost:9000/minio/health/live";

    @Autowired
    private ImageStorage storage;

    @Autowired
    private StorageProperties properties;

    @Test
    void storesTheFileAndServesItByThePublicUrl() throws Exception {
        byte[] content = "not-really-a-png".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "item.png", "image/png", content);

        String key = storage.store(file);

        assertThat(key).as("ключ розкладений по роках і місяцях").matches("items/\\d{4}/\\d{2}/[-\\da-f]+\\.png");

        try {
            HttpURLConnection connection = open(properties.urlOf(key));
            assertThat(connection.getResponseCode()).as("бакет віддає картинку анонімно").isEqualTo(200);
            assertThat(connection.getInputStream().readAllBytes()).isEqualTo(content);
        } finally {
            storage.delete(key);
        }

        assertThat(open(properties.urlOf(key)).getResponseCode()).as("після видалення").isEqualTo(404);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        return connection;
    }

    @SuppressWarnings("unused")
    static boolean minioIsUp() {
        try {
            return open(HEALTH).getResponseCode() == 200;
        } catch (Exception unreachable) {
            return false;
        }
    }
}
