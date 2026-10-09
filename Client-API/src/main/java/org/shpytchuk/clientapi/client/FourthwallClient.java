package org.shpytchuk.clientapi.client;

import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.exeption.PaymentUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Slf4j
public class FourthwallClient {

    static final String PRODUCTS = "/products";
    static final String IMAGES = "/images";
    static final String DIGITAL_FILES = "/digital-files";
    static final String ORDERS = "/order";
    static final String DIGITAL = "digital";

    private static final String FOURTHWALL = "Fourthwall";
    private static final String STORAGE = "The file storage";
    private static final MediaType FILE_TYPE = MediaType.parseMediaType(FourthwallProperties.ProductFile.CONTENT_TYPE);

    private final RestClient restClient;
    private final RestClient storageClient;
    private final FourthwallProperties.ProductImage image;
    private final FourthwallProperties.ProductFile file;

    public FourthwallClient(RestClient restClient, RestClient storageClient,
                            FourthwallProperties.ProductImage image, FourthwallProperties.ProductFile file) {
        this.restClient = restClient;
        this.storageClient = storageClient;
        this.image = image;
        this.file = file;
    }

    public static FourthwallClient of(RestClient.Builder builder, FourthwallProperties properties) {
        RestClient storageClient = builder.clone().build();
        return new FourthwallClient(builder
                .baseUrl(properties.apiUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build(), storageClient, properties.productImage(), properties.productFile());
    }

    public FourthwallProduct createDigitalProduct(String name, String description, BigDecimal price) {
        JsonNode created = call(() -> restClient.post()
                .uri(PRODUCTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "type", DIGITAL,
                        "name", name,
                        "description", description,
                        "price", price,
                        "publishOnCreate", false))
                .retrieve()
                .body(JsonNode.class));
        String productId = text(created, "productId");
        if (productId.isEmpty()) {
            throw new PaymentUnavailableException("Fourthwall created a product without an id: " + created);
        }
        String variantId = variantOf(attachImage(productId));
        attachFile(productId);
        if (variantId.isEmpty()) {
            variantId = variantOf(call(() -> restClient.get()
                    .uri(PRODUCTS + "/{id}", productId)
                    .retrieve()
                    .body(JsonNode.class)));
        }
        if (variantId.isEmpty()) {
            throw new PaymentUnavailableException("Fourthwall product " + productId + " has no variant");
        }
        log.debug("Created Fourthwall product {} with variant {}", productId, variantId);
        return new FourthwallProduct(productId, variantId);
    }

    public void markDownloaded(String orderId, String defaultFileUrl) {
        send(FOURTHWALL, () -> restClient.put()
                .uri(ORDERS + "/{id}/downloaded", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("defaultFileUrl", defaultFileUrl))
                .retrieve()
                .toBodilessEntity());
        log.debug("Fourthwall order {} marked downloaded", orderId);
    }

    private JsonNode attachImage(String productId) {
        if (image == null) {
            return null;
        }
        try {
            return call(() -> restClient.post()
                    .uri(PRODUCTS + "/{id}" + IMAGES, productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("images", List.of(Map.of(
                            "url", image.url(),
                            "width", image.width(),
                            "height", image.height()))))
                    .retrieve()
                    .body(JsonNode.class));
        } catch (PaymentUnavailableException e) {
            log.warn("Fourthwall product {} stays without an image: {}", productId, getMostSpecificCause(e).toString());
            return null;
        }
    }

    private void attachFile(String productId) {
        if (file == null) {
            return;
        }
        byte[] bytes = file.bytes();
        try {
            JsonNode upload = call(() -> restClient.post()
                    .uri(PRODUCTS + "/{id}" + DIGITAL_FILES + "/upload-url", productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "fileName", file.name(),
                            "contentType", FourthwallProperties.ProductFile.CONTENT_TYPE,
                            "size", bytes.length))
                    .retrieve()
                    .body(JsonNode.class));
            String uploadUrl = text(upload, "uploadUrl");
            String fileUrl = text(upload, "fileUrl");
            if (uploadUrl.isEmpty() || fileUrl.isEmpty()) {
                throw new PaymentUnavailableException("Fourthwall gave no upload URL: " + upload);
            }
            URI signed = signedUri(uploadUrl);
            send(STORAGE, () -> storageClient.put()
                    .uri(signed)
                    .contentType(FILE_TYPE)
                    .header("x-goog-content-length-range", "0," + bytes.length)
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity());
            send(FOURTHWALL, () -> restClient.post()
                    .uri(PRODUCTS + "/{id}" + DIGITAL_FILES, productId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fileUrl", fileUrl, "fileName", file.name()))
                    .retrieve()
                    .toBodilessEntity());
        } catch (PaymentUnavailableException e) {
            log.warn("Fourthwall product {} stays without a file, its orders stay cancellable: {}",
                    productId, getMostSpecificCause(e).toString());
        }
    }

    private static String variantOf(JsonNode product) {
        return product == null ? "" : text(product.path("variants").path(0), "id");
    }

    private static JsonNode call(Call call) {
        try {
            JsonNode node = call.run();
            if (node == null) {
                throw new PaymentUnavailableException("Fourthwall answered with an empty body");
            }
            return node;
        } catch (RestClientException e) {
            throw unavailable(FOURTHWALL, e);
        }
    }

    private static void send(String server, Runnable request) {
        try {
            request.run();
        } catch (RestClientException e) {
            throw unavailable(server, e);
        }
    }

    private static URI signedUri(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new PaymentUnavailableException("Fourthwall gave a malformed upload URL", e);
        }
        if (!uri.isAbsolute()) {
            throw new PaymentUnavailableException("Fourthwall gave a relative upload URL");
        }
        return uri;
    }

    private static PaymentUnavailableException unavailable(String server, RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return new PaymentUnavailableException("%s answered %d: %s".formatted(
                    server, response.getStatusCode().value(), response.getResponseBodyAsString()), e);
        }
        return new PaymentUnavailableException(server + " is unreachable: " + e.getMessage(), e);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue().strip() : "";
    }

    @FunctionalInterface
    private interface Call {
        JsonNode run();
    }
}
