package org.shpytchuk.telegramingest.api;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.geo.ResolvedPlace;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;
import org.shpytchuk.telegramingest.model.SocialMedia;

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
