package org.shpytchuk.ingest.pipeline;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.place.ResolvedPlace;

import java.time.LocalDate;
import java.util.List;

public record Notice(ItemKind kind, String title, @Nullable String description, LocalDate date,
                     @Nullable Money compensation, long categoryId, ResolvedPlace place, String phone,
                     List<SocialMedia> socialMedias, byte @Nullable [] photo, String phoneHash) {
}
