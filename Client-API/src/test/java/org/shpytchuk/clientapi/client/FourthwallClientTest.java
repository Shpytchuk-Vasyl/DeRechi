package org.shpytchuk.clientapi.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.exeption.PaymentUnavailableException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FourthwallClientTest {

    private static final String API = "https://api.fourthwall.com/open-api/v1.0";
    private static final String PRODUCT = """
            {"id":"prod-1","name":"Author's phone number (lost-42)","access":{"type":"HIDDEN"},
             "variants":[{"id":"var-1","name":"Author's phone number (lost-42)","unitPrice":{"value":1,"currency":"USD"}}]}
            """;

    private static final String IMAGE = "https://cdn.fourthwall.com/media/derechi-product.png";

    private MockRestServiceServer server;
    private FourthwallClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = FourthwallClient.of(builder, properties(null));
    }

    private static FourthwallProperties properties(FourthwallProperties.ProductImage image) {
        return new FourthwallProperties(API + "/", "https://derechi-shop.fourthwall.com",
                "api-user", "api-password", "secret", BigDecimal.ONE, "Author's phone number (%s)", "Sent after the payment.",
                Duration.ofSeconds(3), Duration.ofSeconds(10), image);
    }

    private FourthwallClient clientWithImage() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return FourthwallClient.of(builder, properties(new FourthwallProperties.ProductImage(IMAGE, 600, 800)));
    }

    @Test
    void createsAHiddenDigitalProductWithBasicAuthAndReadsItsVariant() {
        server.expect(requestTo(API + "/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YXBpLXVzZXI6YXBpLXBhc3N3b3Jk"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("digital"))
                .andExpect(jsonPath("$.name").value("Author's phone number (lost-42)"))
                .andExpect(jsonPath("$.description").value("Sent after the payment."))
                .andExpect(jsonPath("$.price").value(1))
                .andExpect(jsonPath("$.publishOnCreate").value(false))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"productId\":\"prod-1\",\"images\":[]}"));
        server.expect(requestTo(API + "/products/prod-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic YXBpLXVzZXI6YXBpLXBhc3N3b3Jk"))
                .andRespond(withSuccess(PRODUCT, MediaType.APPLICATION_JSON));

        FourthwallProduct product = client.createDigitalProduct("Author's phone number (lost-42)", "Sent after the payment.",
                BigDecimal.ONE);

        assertThat(product).isEqualTo(new FourthwallProduct("prod-1", "var-1"));
        server.verify();
    }

    @Test
    void attachesTheConfiguredImageAndTakesTheVariantFromItsAnswerWithoutAGet() {
        FourthwallClient client = clientWithImage();
        server.expect(requestTo(API + "/products"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"productId\":\"prod-1\",\"images\":[]}"));
        server.expect(requestTo(API + "/products/prod-1/images"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic YXBpLXVzZXI6YXBpLXBhc3N3b3Jk"))
                .andExpect(jsonPath("$.images[0].url").value(IMAGE))
                .andExpect(jsonPath("$.images[0].width").value(600))
                .andExpect(jsonPath("$.images[0].height").value(800))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(PRODUCT));

        assertThat(client.createDigitalProduct("n", "d", BigDecimal.ONE)).isEqualTo(new FourthwallProduct("prod-1", "var-1"));
        server.verify();
    }

    @Test
    void sellsTheProductWithoutAnImageAndReadsTheVariantWhenAttachingItFails() {
        FourthwallClient client = clientWithImage();
        server.expect(requestTo(API + "/products"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"productId\":\"prod-1\",\"images\":[]}"));
        server.expect(requestTo(API + "/products/prod-1/images"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"OFFER_MODIFICATION_FORBIDDEN_ERROR\"}"));
        server.expect(requestTo(API + "/products/prod-1"))
                .andRespond(withSuccess(PRODUCT, MediaType.APPLICATION_JSON));

        assertThat(client.createDigitalProduct("n", "d", BigDecimal.ONE)).isEqualTo(new FourthwallProduct("prod-1", "var-1"));
        server.verify();
    }

    @Test
    void reportsTheRateLimitAsPaymentUnavailable() {
        server.expect(requestTo(API + "/products"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"OPEN_API_TOO_MANY_REQUESTS\",\"message\":\"Too many requests.\"}"));

        assertThatThrownBy(() -> client.createDigitalProduct("n", "d", BigDecimal.ONE))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("Too many requests");
    }

    @Test
    void reportsAProductThatCannotBeReadBackAsPaymentUnavailable() {
        server.expect(requestTo(API + "/products"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"productId\":\"prod-1\",\"images\":[]}"));
        server.expect(requestTo(API + "/products/prod-1")).andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.createDigitalProduct("n", "d", BigDecimal.ONE))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("404");
    }

    @Test
    void reportsAProductWithoutAVariantAsPaymentUnavailable() {
        server.expect(requestTo(API + "/products"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"productId\":\"prod-1\",\"images\":[]}"));
        server.expect(requestTo(API + "/products/prod-1"))
                .andRespond(withSuccess("{\"id\":\"prod-1\",\"variants\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createDigitalProduct("n", "d", BigDecimal.ONE))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("no variant");
    }

    @Test
    void reportsAnAnswerWithoutAProductIdAsPaymentUnavailable() {
        server.expect(requestTo(API + "/products"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"images\":[]}"));

        assertThatThrownBy(() -> client.createDigitalProduct("n", "d", BigDecimal.ONE))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("without an id");
    }
}
