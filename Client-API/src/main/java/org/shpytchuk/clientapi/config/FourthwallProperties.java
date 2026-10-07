package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "derechi.fourthwall")
public record FourthwallProperties(
        String apiUrl,
        String shopUrl,
        String username,
        String password,
        String webhookSecret,
        BigDecimal price,
        String productName,
        String productDescription,
        Duration connectTimeout,
        Duration readTimeout,
        ProductImage productImage
) {

    public static final String SIGNATURE_HEADER = "X-Fourthwall-Hmac-SHA256";

    private static final String HMAC = "HmacSHA256";

    public FourthwallProperties {
        apiUrl = stripSlash(require(apiUrl, "api-url"));
        shopUrl = stripSlash(require(shopUrl, "shop-url"));
        require(username, "username");
        require(password, "password");
        require(webhookSecret, "webhook-secret");
        require(productName, "product-name");
        require(productDescription, "product-description");
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("derechi.fourthwall.price must be positive: " + price);
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(readTimeout, "read-timeout");
        if (productImage != null && (productImage.url() == null || productImage.url().isBlank())) {
            productImage = null;
        }
    }

    public String checkoutUrl(String variantId) {
        return shopUrl + "/cart/checkout?products=" + variantId + ":1";
    }

    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean accepts(byte[] body, String signature) {
        return signature != null && MessageDigest.isEqual(
                sign(body).getBytes(StandardCharsets.UTF_8),
                signature.strip().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "FourthwallProperties[apiUrl=" + apiUrl + ", shopUrl=" + shopUrl + ", username=" + username
                + ", password=***, webhookSecret=***, price=" + price
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", productImage=" + productImage + "]";
    }

    public record ProductImage(String url, int width, int height) {

        public ProductImage {
            if (url != null && !url.isBlank() && (width <= 0 || height <= 0)) {
                throw new IllegalArgumentException(
                        "derechi.fourthwall.product-image needs a positive width and height: " + width + "x" + height);
            }
            url = url == null ? null : url.strip();
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("derechi.fourthwall." + name + " must be set");
        }
        return value.strip();
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("derechi.fourthwall." + name + " must be positive: " + value);
        }
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
