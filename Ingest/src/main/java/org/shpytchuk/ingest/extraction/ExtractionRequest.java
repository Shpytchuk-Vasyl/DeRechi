package org.shpytchuk.ingest.extraction;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Input for one model call.
 *
 * @param maskedText post text with phones and emails already masked
 * @param postDate   date of the post in the time zone of its chat, for relative dates ("yesterday")
 * @param categories category keys the model may choose from
 * @param photos     redacted JPEGs, at most {@code derechi.ingest.max-photos}
 */
public record ExtractionRequest(String maskedText, LocalDate postDate, LocalDate today, @Nullable String chatCity,
                                List<String> categories, List<byte[]> photos) {
}
