package org.shpytchuk.clientapi.language;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public enum SearchLanguage {

    ENGLISH("en", "english"),
    GERMAN("de", "german"),
    FRENCH("fr", "french"),
    SPANISH("es", "spanish"),
    ITALIAN("it", "italian"),
    RUSSIAN("ru", "russian"),
    UKRAINIAN("uk", "ukrainian"),
    POLISH("pl", "polish"),

    SIMPLE(null, "simple");

    private final String isoCode;
    private final String regconfig;

    SearchLanguage(String isoCode, String regconfig) {
        this.isoCode = isoCode;
        this.regconfig = regconfig;
    }

    public String isoCode() {
        return isoCode;
    }

    public String regconfig() {
        return regconfig;
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
