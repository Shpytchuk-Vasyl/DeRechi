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
import java.util.Map;

public class FourthwallClient {

    private static final Logger log = LoggerFactory.getLogger(FourthwallClient.class);

    static final String PRODUCTS = "/products";
    static final String DIGITAL = "digital";

    private final RestClient restClient;

    public FourthwallClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static FourthwallClient of(RestClient.Builder builder, FourthwallProperties properties) {
        return new FourthwallClient(builder
                .baseUrl(properties.apiUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password()))
                .build());
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

        JsonNode product = call(() -> restClient.get()
                .uri(PRODUCTS + "/{id}", productId)
                .retrieve()
                .body(JsonNode.class));
        String variantId = text(product.path("variants").path(0), "id");
        if (variantId.isEmpty()) {
            throw new PaymentUnavailableException("Fourthwall product " + productId + " has no variant");
        }
        log.info("Created Fourthwall product {} with variant {}", productId, variantId);
        return new FourthwallProduct(productId, variantId);
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
