package org.shpytchuk.clientapi.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", " ", "pw", "s", BigDecimal.ONE, "n", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", null, BigDecimal.ONE, "n", "d"))
                .hasMessageContaining("webhook-secret");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "https://shop", "u", "pw", "s", BigDecimal.ZERO, "n", "d"))
                .hasMessageContaining("price");
        assertThatThrownBy(() -> new FourthwallProperties("https://api", "", "u", "pw", "s", BigDecimal.ONE, "n", "d"))
                .hasMessageContaining("shop-url");
    }

    @Test
    void hidesTheSecretsInToString() {
        assertThat(properties("https://shop").toString())
                .contains("username=api-user")
                .doesNotContain("api-password", "webhook-secret-value");
    }

    private static FourthwallProperties properties(String shopUrl) {
        return new FourthwallProperties("https://api.fourthwall.com/open-api/v1.0/", shopUrl, "api-user", "api-password",
                "webhook-secret-value", BigDecimal.ONE, "Author's phone number (%s)", "Sent after the payment.");
    }
}
