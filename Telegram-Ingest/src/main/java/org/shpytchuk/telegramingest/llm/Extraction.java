package org.shpytchuk.telegramingest.llm;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;
import org.shpytchuk.telegramingest.model.SocialMedia;

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
