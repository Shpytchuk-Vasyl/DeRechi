package org.shpytchuk.ingest.place.google;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextSearchPlaceResolverTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Район БАМ, вулиця Гетьмана Мазепи, 171, Івано-Франківськ, Івано-Франківська область, 76000"
                    + "|Район БАМ, вулиця Гетьмана Мазепи, 171, Івано-Франківськ",
            "Rynek 1, Kraków, województwo małopolskie, 31-042|Rynek 1, Kraków",
            "вулиця Пушкіна, 1, Сімферополь, Автономна Республіка Крим|вулиця Пушкіна, 1, Сімферополь",
            "вулиця Наукова, Львів|вулиця Наукова, Львів",
            "Львів, Львівська область|Львів",
            "Івано-Франківська область|Івано-Франківська область",
            "76000|76000",
            "вулиця Шевченка, 5|вулиця Шевченка, 5",
            "проспект Свободи, 100|проспект Свободи, 100"})
    void dropsTheRegionAndThePostalCodeAtTheEnd(String name, String expected) {
        assertThat(TextSearchPlaceResolver.withoutRegion(name)).isEqualTo(expected);
    }
}
