package org.shpytchuk.clientapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "derechi.countries")
public record CountriesProperties(List<String> supported, String fallback) {

    public CountriesProperties {
        Assert.notEmpty(supported, "derechi.countries.supported must list at least one country");
        supported = supported.stream()
                .map(CountriesProperties::normalise)
                .distinct()
                .toList();
        supported.forEach(CountriesProperties::requireKnownCountry);

        fallback = StringUtils.hasText(fallback) ? normalise(fallback) : supported.getFirst();
        if (!supported.contains(fallback)) {
            throw new IllegalArgumentException(
                    "derechi.countries.fallback " + fallback + " is not among supported " + supported);
        }
    }

    public boolean supports(String code) {
        return code != null && supported.contains(normalise(code));
    }

    /** ISO 4217 code of the currency used in the country, as the JDK knows it (UA → UAH, DE → EUR, ...). */
    public String currencyOf(String countryCode) {
        return currencyOfCountry(normalise(countryCode)).getCurrencyCode();
    }

    public Set<String> currencies() {
        Set<String> currencies = new LinkedHashSet<>();
        supported.forEach(code -> currencies.add(currencyOf(code)));
        return Collections.unmodifiableSet(currencies);
    }

    private static String normalise(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static void requireKnownCountry(String code) {
        if (code.length() != 2) {
            throw new IllegalArgumentException("Country code must be ISO 3166-1 alpha-2: " + code);
        }
        currencyOfCountry(code);
    }

    private static Currency currencyOfCountry(String code) {
        try {
            return Currency.getInstance(Locale.of("", code));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown country or no currency for it: " + code, e);
        }
    }
}
