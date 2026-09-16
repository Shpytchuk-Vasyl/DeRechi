package org.shpytchuk.clientapi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ContactMaskerTest {

    @Test
    void keepsCountryAndOperatorCodeAndTwoLastDigitsOfPhone() {
        assertThat(ContactMasker.maskPhone("+380671234567")).isEqualTo("+38067*****67");
    }

    @Test
    void dontMasksTooShortPhone() {
        assertThat(ContactMasker.maskPhone("+380")).isEqualTo("+380");
        assertThat(ContactMasker.maskPhone("")).isEqualTo("");
    }

    @Test
    void keepsFirstAndLastCharacterOfTheLocalPartAndTheWholeDomain() {
        assertThat(ContactMasker.maskEmail("finder@example.com")).isEqualTo("f*****r@example.com");
    }

    @Test
    void masksTheShortestLocalPartThatStillHasSomethingToHide() {
        assertThat(ContactMasker.maskEmail("abc@example.com")).isEqualTo("a*****c@example.com");
    }

    @Test
    void dontMasksLocalPartOfTwoCharactersAndShorter() {
        assertThat(ContactMasker.maskEmail("ab@example.com")).isEqualTo("ab@example.com");
        assertThat(ContactMasker.maskEmail("a@example.com")).isEqualTo("a@example.com");
        assertThat(ContactMasker.maskEmail("@example.com")).isEqualTo("@example.com");
    }

    @Test
    void cutsTheLocalPartAtTheFirstAtSign() {
        assertThat(ContactMasker.maskEmail("fin@der@example.com")).isEqualTo("f*****n@der@example.com");
    }

    @Test
    void masksAValueWithoutAtSignAsAWhole() {
        assertThat(ContactMasker.maskEmail("finder")).isEqualTo("finder");
        assertThat(ContactMasker.maskEmail("fi")).isEqualTo("fi");
    }

    @Test
    void passesNullThrough() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ContactMasker.maskPhone(null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ContactMasker.maskEmail(null));
    }
}
