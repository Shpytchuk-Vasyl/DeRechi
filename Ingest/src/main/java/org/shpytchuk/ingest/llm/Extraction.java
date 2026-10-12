package org.shpytchuk.ingest.llm;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;

import java.time.LocalDate;
import java.util.List;

/**
 * What the model read out of a post; the JSON schema sent to OpenRouter mirrors this record.
 */
public record Extraction(
        @Nullable Boolean isNotice,
        @Nullable ItemKind kind,
        @Nullable String title,
        @Nullable String description,
        @Nullable String category,
        @Nullable LocalDate date,
        @Nullable String placeText,
        @Nullable String city,
        @Nullable Money compensation,
        @Nullable String contactPhoneRef,
        @Nullable List<SocialMedia> socialMedias,
        @Nullable Double confidence,
        @Nullable String rejectReason) {
}
