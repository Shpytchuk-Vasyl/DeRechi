package org.shpytchuk.worker.language;

import java.util.Arrays;
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

    private static final String RANK_FUNCTION_PREFIX = "ts_rank_";
    private static final String MATCH_RANK_FUNCTION_PREFIX = "match_rank_";
    private static final String SHARES_WORDS_FUNCTION_PREFIX = "shares_words_";

    private final String isoCode;
    private final String regconfig;

    SearchLanguage(String isoCode, String regconfig) {
        this.isoCode = isoCode;
        this.regconfig = regconfig;
    }

    public String regconfig() {
        return regconfig;
    }

    public String rankFunction() {
        return RANK_FUNCTION_PREFIX + regconfig;
    }

    public String matchRankFunction() {
        return MATCH_RANK_FUNCTION_PREFIX + regconfig;
    }

    public String sharesWordsFunction() {
        return SHARES_WORDS_FUNCTION_PREFIX + regconfig;
    }

    public static SearchLanguage fromIsoCode(String isoCode) {
        return Arrays.stream(values())
                .filter(language -> Objects.equals(language.isoCode, isoCode))
                .findFirst()
                .orElse(SIMPLE);
    }
}
