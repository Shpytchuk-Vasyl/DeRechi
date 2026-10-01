package org.shpytchuk.clientapi.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CountriesPropertiesTest {

    private static final CountriesProperties DEFAULTS =
            new CountriesProperties(List.of("UA", "PL", "DE", "FR"), "UA");

    @Test
    void uppercasesAndDeduplicatesTheConfiguredCodes() {
        CountriesProperties properties = new CountriesProperties(List.of(" ua", "pl", "PL"), "ua");

        assertThat(properties.supported()).containsExactly("UA", "PL");
        assertThat(properties.fallback()).isEqualTo("UA");
    }

    @Test
    void supportsIsCaseInsensitiveAndNullSafe() {
        assertThat(DEFAULTS.supports("pl")).isTrue();
        assertThat(DEFAULTS.supports("UA")).isTrue();
        assertThat(DEFAULTS.supports("AU")).isFalse();
        assertThat(DEFAULTS.supports(null)).isFalse();
    }

    @Test
    void resolvesTheCurrencyOfACountryThroughTheJdk() {
        assertThat(DEFAULTS.currencyOf("UA")).isEqualTo("UAH");
        assertThat(DEFAULTS.currencyOf("pl")).isEqualTo("PLN");
        assertThat(DEFAULTS.currencyOf("DE")).isEqualTo("EUR");
        assertThat(DEFAULTS.currencyOf("FR")).isEqualTo("EUR");
    }

    @Test
    void collectsTheCurrenciesOfAllSupportedCountriesOnce() {
        assertThat(DEFAULTS.currencies()).containsExactlyInAnyOrder("UAH", "PLN", "EUR");
    }

    @Test
    void fallsBackToTheFirstSupportedCountryWhenNoFallbackIsGiven() {
        assertThat(new CountriesProperties(List.of("pl", "UA"), null).fallback()).isEqualTo("PL");
        assertThat(new CountriesProperties(List.of("pl", "UA"), " ").fallback()).isEqualTo("PL");
    }

    @Test
    void rejectsAFallbackOutsideTheSupportedList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CountriesProperties(List.of("UA", "PL"), "DE"))
                .withMessageContaining("fallback");
    }

    @Test
    void rejectsAnEmptySupportedList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CountriesProperties(List.of(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CountriesProperties(null, null));
    }

    @Test
    void rejectsCodesThatAreNotCountriesWithACurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CountriesProperties(List.of("UKR"), null))
                .withMessageContaining("alpha-2");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CountriesProperties(List.of("XX"), null))
                .withMessageContaining("XX");
    }
}
