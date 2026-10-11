package org.shpytchuk.telegramingest.channel;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;

import java.time.LocalDate;

public record ChannelPost(
        ItemKind kind,
        String title,
        @Nullable String description,
        String placeName,
        LocalDate date,
        @Nullable Money compensation,
        byte @Nullable [] photo,
        @Nullable String itemUrl,
        String sourceTitle) {
}
