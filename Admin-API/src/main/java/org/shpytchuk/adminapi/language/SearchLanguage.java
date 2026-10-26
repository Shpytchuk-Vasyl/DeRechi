package org.shpytchuk.adminapi.language;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public enum SearchLanguage {

    ENGLISH("en"),
    GERMAN("de"),
    FRENCH("fr"),
    SPANISH("es"),
    ITALIAN("it"),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    POLISH("pl"),

    SIMPLE(null);

    private final String isoCode;

    SearchLanguage(String isoCode) {
        this.isoCode = isoCode;
    }

    public String isoCode() {
        return isoCode;
    }

    public static List<SearchLanguage> detectable() {
        return Arrays.stream(values())
                .filter(language -> language.isoCode != null)
                .toList();
    }

    public static SearchLanguage fromIsoCode(String isoCode) {
        return Arrays.stream(values())
                .filter(language -> Objects.equals(language.isoCode, isoCode))
                .findFirst()
                .orElse(SIMPLE);
    }
}
