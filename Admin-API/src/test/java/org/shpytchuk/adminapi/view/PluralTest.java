package org.shpytchuk.adminapi.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluralTest {

    @Test
    void picksTheUkrainianFormByTheLastDigits() {
        assertThat(Plural.records(1)).isEqualTo("1 запис");
        assertThat(Plural.records(2)).isEqualTo("2 записи");
        assertThat(Plural.records(5)).isEqualTo("5 записів");
        assertThat(Plural.records(0)).isEqualTo("0 записів");
    }

    @Test
    void treatsTheTeensAsTheManyForm() {
        assertThat(Plural.records(11)).isEqualTo("11 записів");
        assertThat(Plural.records(12)).isEqualTo("12 записів");
        assertThat(Plural.records(14)).isEqualTo("14 записів");
        assertThat(Plural.records(111)).isEqualTo("111 записів");
    }

    @Test
    void repeatsTheFormsAboveTwenty() {
        assertThat(Plural.records(21)).isEqualTo("21 запис");
        assertThat(Plural.records(22)).isEqualTo("22 записи");
        assertThat(Plural.records(25)).isEqualTo("25 записів");
        assertThat(Plural.records(101)).isEqualTo("101 запис");
    }
}
