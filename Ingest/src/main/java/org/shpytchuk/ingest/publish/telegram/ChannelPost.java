package org.shpytchuk.ingest.publish.telegram;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.publish.Publication;

import java.time.LocalDate;

public record ChannelPost(
        ItemKind kind,
        String title,
        @Nullable String description,
        String placeName,
        LocalDate date,
        @Nullable Money compensation,
        byte @Nullable [] photo,
        @Nullable String itemUrl) {

    static ChannelPost of(Publication publication) {
        Notice notice = publication.notice();
        return new ChannelPost(notice.kind(), notice.title(), notice.description(), notice.place().name(),
                notice.date(), notice.compensation(), notice.photo(), publication.siteItemUrl());
    }
}
