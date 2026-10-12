package org.shpytchuk.ingest.api.derechi;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.geo.ResolvedPlace;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.api.ai.model.Money;
import org.shpytchuk.ingest.api.ai.model.SocialMedia;

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
