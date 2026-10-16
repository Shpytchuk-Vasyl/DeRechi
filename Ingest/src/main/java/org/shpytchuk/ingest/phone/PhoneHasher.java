package org.shpytchuk.ingest.phone;

import org.shpytchuk.ingest.config.IngestProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 of a contact (an E.164 number or {@code AuthorProfile.contactKey()}), for
 * {@code ingest_post.phone_hash}. Keyed, so the column cannot be reversed by hashing every possible number.
 */
@Component
public class PhoneHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PhoneHasher(IngestProperties properties) {
        this.key = new SecretKeySpec(properties.hashKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String contact) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(contact.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
