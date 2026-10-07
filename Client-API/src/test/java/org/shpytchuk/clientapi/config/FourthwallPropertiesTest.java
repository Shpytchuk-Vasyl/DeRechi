package org.shpytchuk.clientapi.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FourthwallPropertiesTest {

    private static final byte[] BODY = "{\"type\":\"ORDER_PLACED\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void buildsTheDirectCheckoutOfTheShopForAVariant() {
        FourthwallProperties properties = properties("https://derechi-shop.fourthwall.com/");

        assertThat(properties.shopUrl()).isEqualTo("https://derechi-shop.fourthwall.com");
        assertThat(properties.checkoutUrl("var-1"))
                .isEqualTo("https://derechi-shop.fourthwall.com/cart/checkout?products=var-1:1");
    }

    @Test
    void signsTheBodyWithHmacSha256InBase64() {
        FourthwallProperties properties = properties("https://derechi-shop.fourthwall.com");

        assertThat(properties.sign(BODY)).isEqualTo("vn7Twts8G/01T3mcxrh7KYY8SxXEM5zCczBRuYSRdaQ=");
    }

    @Test
    void acceptsOnlyTheMatchingSignature() {
        FourthwallProperties properties = properties("https://derechi-shop.fourthwall.com");
        String signature = properties.sign(BODY);

        assertThat(properties.accepts(BODY, signature)).isTrue();
        assertThat(properties.accepts(BODY, " " + signature + "\n")).isTrue();
        assertThat(properties.accepts(BODY, signature.substring(1))).isFalse();
        assertThat(properties.accepts("{\"type\":\"DONATION\"}".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(properties.accepts(BODY, null)).isFalse();
        assertThat(properties.accepts(BODY, "")).isFalse();
    }

    @Test
    void refusesToStartWithoutCredentialsOrAPrice() {
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", " ", "pw", "s", BigDecimal.ONE, "n", "d", Duration.ofSeconds(3), Duration.ofSeconds(10), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", null, BigDecimal.ONE, "n", "d", Duration.ofSeconds(3), Duration.ofSeconds(10), null, null))
                .hasMessageContaining("webhook-secret");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", "s", BigDecimal.ZERO, "n", "d", Duration.ofSeconds(3), Duration.ofSeconds(10), null, null))
                .hasMessageContaining("price");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "", "u", "pw", "s", BigDecimal.ONE, "n", "d", Duration.ofSeconds(3), Duration.ofSeconds(10), null, null))
                .hasMessageContaining("shop-url");
    }

    @Test
    void refusesToStartWithoutTimeouts() {
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", "s", BigDecimal.ONE,
                "n", "d", null, Duration.ofSeconds(10), null, null))
                .hasMessageContaining("connect-timeout");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", "s", BigDecimal.ONE,
                "n", "d", Duration.ofSeconds(3), Duration.ZERO, null, null))
                .hasMessageContaining("read-timeout");
    }

    @Test
    void treatsABlankProductImageAsNone() {
        assertThat(withImage(new FourthwallProperties.ProductImage(" ", 0, 0)).productImage()).isNull();
        assertThat(withImage(new FourthwallProperties.ProductImage(null, 0, 0)).productImage()).isNull();
        assertThat(withImage(null).productImage()).isNull();
    }

    @Test
    void keepsAProductImageWithItsSize() {
        FourthwallProperties.ProductImage image = withImage(
                new FourthwallProperties.ProductImage(" https://cdn.fourthwall.com/media/derechi.png ", 600, 800)).productImage();

        assertThat(image).isEqualTo(new FourthwallProperties.ProductImage("https://cdn.fourthwall.com/media/derechi.png", 600, 800));
    }

    @Test
    void refusesAProductImageWithoutASize() {
        assertThatThrownBy(() -> new FourthwallProperties.ProductImage("https://cdn.fourthwall.com/media/derechi.png", 600, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product-image");
    }

    @Test
    void refusesAProductImageWithoutTheScheme() {
        // `${VAR=https://...}` resolves to the part after the first colon
        assertThatThrownBy(() -> new FourthwallProperties.ProductImage("//cdn.fourthwall.com/media/derechi.png", 600, 800))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void treatsABlankProductFileAsNoneAndRefusesOneWithoutAName() {
        assertThat(withFile(new FourthwallProperties.ProductFile("derechi.txt", " ")).productFile()).isNull();
        assertThat(withFile(new FourthwallProperties.ProductFile(" derechi.txt ", "text")).productFile())
                .isEqualTo(new FourthwallProperties.ProductFile("derechi.txt", "text"));
        assertThatThrownBy(() -> new FourthwallProperties.ProductFile(" ", "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product-file");
    }

    @Test
    void hidesTheSecretsInToString() {
        assertThat(properties("https://shop").toString())
                .contains("username=api-user")
                .doesNotContain("api-password", "webhook-secret-value");
    }

    private static FourthwallProperties properties(String shopUrl) {
        return new FourthwallProperties("https://api.fourthwall.com/open-api/v1.0/", shopUrl, "api-user", "api-password",
                "webhook-secret-value", BigDecimal.ONE, "Author's phone number (%s)", "Sent after the payment.", Duration.ofSeconds(3), Duration.ofSeconds(10), null, null);
    }

    private static FourthwallProperties withImage(FourthwallProperties.ProductImage image) {
        return new FourthwallProperties("https://api.fourthwall.com/open-api/v1.0/", "https://shop", "api-user", "api-password",
                "webhook-secret-value", BigDecimal.ONE, "Author's phone number (%s)", "Sent after the payment.", Duration.ofSeconds(3), Duration.ofSeconds(10), image, null);
    }

    private static FourthwallProperties withFile(FourthwallProperties.ProductFile file) {
        return new FourthwallProperties("https://api.fourthwall.com/open-api/v1.0/", "https://shop", "api-user", "api-password",
                "webhook-secret-value", BigDecimal.ONE, "Author's phone number (%s)", "Sent after the payment.", Duration.ofSeconds(3), Duration.ofSeconds(10), null, file);
    }
}
