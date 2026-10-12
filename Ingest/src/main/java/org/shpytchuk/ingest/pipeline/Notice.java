package org.shpytchuk.ingest.pipeline;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.derechi.NewItem;
import org.shpytchuk.ingest.channel.ChannelPost;
import org.shpytchuk.ingest.api.geo.ResolvedPlace;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.api.ai.model.Money;
import org.shpytchuk.ingest.api.ai.model.SocialMedia;
import org.shpytchuk.ingest.source.IncomingPost;

import java.time.LocalDate;
import java.util.List;

public record Notice(ItemKind kind, String title, @Nullable String description, LocalDate date,
                     @Nullable Money compensation, long categoryId, ResolvedPlace place, String phone,
                     List<SocialMedia> socialMedias, byte @Nullable [] photo, String phoneHash) {

    public NewItem newItem(@Nullable String imageKey) {
        return new NewItem(kind, title, description, date, compensation, imageKey, categoryId, place, phone,
                socialMedias);
    }

    public ChannelPost channelPost(IncomingPost post, @Nullable String itemUrl) {
        return new ChannelPost(kind, title, description, place.name(), date, compensation, photo, itemUrl,
                post.sourceTitle());
    }
}
