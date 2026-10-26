package org.shpytchuk.clientapi.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageResolverTest {

    private static final LanguageResolver RESOLVER = new LanguageResolver();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Lost black leather wallet with my driving licence near the station, ENGLISH",
            "Загубила чорний шкіряний гаманець з посвідченням біля вокзалу, UKRAINIAN",
            "Потерял чёрный кожаный кошелёк с правами возле вокзала, RUSSIAN",
            "Schwarze Lederbrieftasche mit Führerschein am Bahnhof verloren, GERMAN",
            "Portefeuille noir en cuir perdu près de la gare avec mon permis, FRENCH",
            "Cartera negra de cuero perdida cerca de la estación con mi carné, SPANISH",
            "Portafoglio nero di pelle perso vicino alla stazione con la patente, ITALIAN",
            "Zgubiłam czarny skórzany portfel z prawem jazdy przy dworcu, POLISH"
    })
    void detectsTheLanguageTheTextIsWrittenIn(String text, SearchLanguage expected) {
        assertThat(RESOLVER.resolve(text)).isEqualTo(expected);
    }

    @Test
    void readsTheTitleAndTheDescriptionTogether() {
        assertThat(RESOLVER.resolve("Гаманець", "Чорний шкіряний, з посвідченням водія, загубила біля вокзалу"))
                .isEqualTo(SearchLanguage.UKRAINIAN);
    }

    @Test
    void skipsAMissingDescription() {
        assertThat(RESOLVER.resolve("Zgubiłam czarny skórzany portfel z prawem jazdy przy dworcu", null))
                .isEqualTo(SearchLanguage.POLISH);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345", "!!!"})
    void fallsBackToSimpleWhenThereIsNothingToDetect(String text) {
        assertThat(RESOLVER.resolve(text, text)).isEqualTo(SearchLanguage.SIMPLE);
    }

    @Test
    void simpleHasNoIsoCodeToStore() {
        assertThat(SearchLanguage.SIMPLE.isoCode()).isNull();
        assertThat(SearchLanguage.fromIsoCode(null)).isEqualTo(SearchLanguage.SIMPLE);
        assertThat(SearchLanguage.fromIsoCode("pt")).isEqualTo(SearchLanguage.SIMPLE);
    }
}
