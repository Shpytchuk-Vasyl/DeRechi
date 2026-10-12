package org.shpytchuk.ingest.api.ai.llm;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.api.ai.model.Money;
import org.shpytchuk.ingest.api.ai.model.SocialMedia;

import java.time.LocalDate;
import java.util.List;


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
