package org.shpytchuk.worker.language;

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
    void picksTheFullTextConfigurationOfTheTitleLanguage(String title, SearchLanguage expected) {
        assertThat(RESOLVER.resolve(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12345", "!!!"})
    void fallsBackToSimpleWhenThereIsNothingToDetect(String title) {
        assertThat(RESOLVER.resolve(title)).isEqualTo(SearchLanguage.SIMPLE);
    }
}
