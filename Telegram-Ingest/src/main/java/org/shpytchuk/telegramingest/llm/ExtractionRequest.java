package org.shpytchuk.telegramingest.llm;

import java.time.LocalDate;
import java.util.List;

/**
 * Input for one model call.
 *
 * @param maskedText post text with phones and emails already masked
 * @param postDate   date of the post in Europe/Kyiv, for relative dates ("yesterday")
 * @param categories category keys the model may choose from
 * @param photos     redacted JPEGs, at most {@code derechi.ingest.max-photos}
 */
public record ExtractionRequest(String maskedText, LocalDate postDate, LocalDate today,
                                List<String> categories, List<byte[]> photos) {
}
