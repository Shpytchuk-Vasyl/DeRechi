package org.shpytchuk.ingest.client;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.place.ResolvedPlace;

import java.time.LocalDate;
import java.util.List;

public record NewItem(
        ItemKind kind,
        String title,
        @Nullable String description,
        LocalDate date,
        @Nullable Money compensation,
        @Nullable String imageKey,
        long categoryId,
        ResolvedPlace place,
        String phone,
        List<SocialMedia> socialMedias) {
}
