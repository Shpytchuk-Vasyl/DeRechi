package org.shpytchuk.clientapi.input;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContactInfoInputTest {

    @Test
    void normalisesTheClaimantsEmailOnTheWayIn() {
        ContactInfoInput input = new ContactInfoInput("+380671234567", "Vasyl.S+12@GoogleMail.com", List.of());

        assertThat(input.email()).isEqualTo("vasyls@gmail.com");
    }
}
