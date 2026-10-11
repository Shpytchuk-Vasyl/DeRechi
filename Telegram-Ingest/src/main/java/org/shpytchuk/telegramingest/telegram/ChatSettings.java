package org.shpytchuk.telegramingest.telegram;

import org.jspecify.annotations.Nullable;

/**
 * Per-source defaults: {@code region} is the ISO 3166-1 alpha-2 used to read phone numbers written
 * without a country code and to bias geocoding, {@code defaultCity} is appended to places that
 * name no city, {@code locale} is the language of geocoding results.
 */
public record ChatSettings(String region, @Nullable String defaultCity, String locale) {
}
