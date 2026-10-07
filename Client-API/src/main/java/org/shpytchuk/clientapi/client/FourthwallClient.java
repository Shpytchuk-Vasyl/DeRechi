package org.shpytchuk.clientapi.client;

import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.exeption.PaymentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class FourthwallClient {

    private static final Logger log = LoggerFactory.getLogger(FourthwallClient.class);

    static final String PRODUCTS = "/products";
    static final String IMAGES = "/images";
    static final String DIGITAL = "digital";

    private final RestClient restClient;
    private final FourthwallProperties.ProductImage image;

    public FourthwallClient(RestClient restClient, FourthwallProperties.ProductImage image) {
        this.restClient = restClient;
        this.image = image;
    }

    public static FourthwallClient of(RestClient.Builder builder, FourthwallProperties properties) {
        return new FourthwallClient(builder
                .baseUrl(properties.apiUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build(), properties.productImage());
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
        if (variantId.isEmpty()) {
            variantId = variantOf(call(() -> restClient.get()
                    .uri(PRODUCTS + "/{id}", productId)
                    .retrieve()
                    .body(JsonNode.class)));
        }
        if (variantId.isEmpty()) {
            throw new PaymentUnavailableException("Fourthwall product " + productId + " has no variant");
        }
        log.info("Created Fourthwall product {} with variant {}", productId, variantId);
        return new FourthwallProduct(productId, variantId);
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
            log.warn("Fourthwall product {} stays without an image: {}", productId, e.getMessage());
            return null;
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
        } catch (RestClientResponseException e) {
            throw new PaymentUnavailableException(
                    "Fourthwall answered %d: %s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new PaymentUnavailableException("Fourthwall is unreachable: " + e.getMessage(), e);
        }
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
