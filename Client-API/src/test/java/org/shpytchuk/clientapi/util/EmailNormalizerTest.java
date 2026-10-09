package org.shpytchuk.clientapi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void lowersTheCaseAndTrims() {
        assertThat(EmailNormalizer.normalize("  Olena@Example.COM ")).isEqualTo("olena@example.com");
    }

    @Test
    void dropsThePlusTagOnAnyDomain() {
        assertThat(EmailNormalizer.normalize("vasyl+12@gmail.com")).isEqualTo("vasyl@gmail.com");
        assertThat(EmailNormalizer.normalize("vasyl+derechi+2@outlook.com")).isEqualTo("vasyl@outlook.com");
    }

    @Test
    void dropsDotsAndAliasDomainOnGmailOnly() {
        assertThat(EmailNormalizer.normalize("Vasyl.S+12@GoogleMail.com")).isEqualTo("vasyls@gmail.com");
        assertThat(EmailNormalizer.normalize("vasyl.s@outlook.com")).isEqualTo("vasyl.s@outlook.com");
    }

    @Test
    void returnsAnUnparsableAddressAsItWasForValidationToReject() {
        assertThat(EmailNormalizer.normalize("not-an-email")).isEqualTo("not-an-email");
    }

    @Test
    void passesNullThrough() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
