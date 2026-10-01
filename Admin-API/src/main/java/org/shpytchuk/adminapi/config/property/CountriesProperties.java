package org.shpytchuk.adminapi.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

@ConfigurationProperties(prefix = "derechi.countries")
public record CountriesProperties(List<String> supported, String fallback) {

    public CountriesProperties {
        if (supported == null || supported.isEmpty()) {
            throw new IllegalArgumentException("derechi.countries.supported must list at least one country");
        }
        supported = supported.stream()
                .map(CountriesProperties::normalise)
                .distinct()
                .toList();
        supported.forEach(CountriesProperties::requireKnownCountry);

        fallback = fallback == null || fallback.isBlank() ? supported.getFirst() : normalise(fallback);
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
