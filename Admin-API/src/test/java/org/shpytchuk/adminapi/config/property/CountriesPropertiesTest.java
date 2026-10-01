package org.shpytchuk.adminapi.config.property;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountriesPropertiesTest {

    @Test
    void uppercasesAndDeduplicatesCodes() {
        CountriesProperties countries = new CountriesProperties(List.of(" ua", "pl", "PL", "De"), "pl");

        assertThat(countries.supported()).containsExactly("UA", "PL", "DE");
        assertThat(countries.fallback()).isEqualTo("PL");
    }

    @Test
    void supportsIsCaseInsensitiveAndNullSafe() {
        CountriesProperties countries = new CountriesProperties(List.of("UA", "PL"), null);

        assertThat(countries.supports("ua")).isTrue();
        assertThat(countries.supports("PL")).isTrue();
        assertThat(countries.supports("de")).isFalse();
        assertThat(countries.supports(null)).isFalse();
        assertThat(countries.supports("")).isFalse();
    }

    @Test
    void currencyComesFromTheJdkTables() {
        CountriesProperties countries = new CountriesProperties(List.of("UA", "PL", "DE", "FR"), "UA");

        assertThat(countries.currencyOf("UA")).isEqualTo("UAH");
        assertThat(countries.currencyOf("pl")).isEqualTo("PLN");
        assertThat(countries.currencyOf("DE")).isEqualTo("EUR");
        assertThat(countries.currencies()).containsExactly("UAH", "PLN", "EUR");
    }

    @Test
    void fallbackDefaultsToTheFirstSupportedCountry() {
        assertThat(new CountriesProperties(List.of("PL", "UA"), null).fallback()).isEqualTo("PL");
        assertThat(new CountriesProperties(List.of("PL", "UA"), "  ").fallback()).isEqualTo("PL");
    }

    @Test
    void rejectsFallbackOutsideTheSupportedList() {
        assertThatThrownBy(() -> new CountriesProperties(List.of("UA", "PL"), "DE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback");
    }

    @Test
    void rejectsEmptyOrUnknownCountries() {
        assertThatThrownBy(() -> new CountriesProperties(List.of(), "UA"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountriesProperties(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountriesProperties(List.of("UA", "ZZ"), "UA"))
                .as("ZZ isn't a country code")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
