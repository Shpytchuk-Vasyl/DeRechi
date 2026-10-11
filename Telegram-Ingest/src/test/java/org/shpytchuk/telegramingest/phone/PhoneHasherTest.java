package org.shpytchuk.telegramingest.phone;

import org.junit.jupiter.api.Test;
import org.shpytchuk.telegramingest.config.IngestProperties;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneHasherTest {

    @Test
    void theSameNumberGivesTheSameHexHash() {
        PhoneHasher hasher = hasher("key");

        assertThat(hasher.hash("+380671234567"))
                .isEqualTo(hasher.hash("+380671234567"))
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isNotEqualTo(hasher.hash("+380501112233"));
    }

    @Test
    void anotherKeyGivesAnotherHash() {
        assertThat(hasher("key").hash("+380671234567")).isNotEqualTo(hasher("other").hash("+380671234567"));
    }

    private static PhoneHasher hasher(String key) {
        return new PhoneHasher(new IngestProperties(null, key, null, 0, null, null, null, 0.6));
    }
}
