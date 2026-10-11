package org.shpytchuk.telegramingest.pipeline;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.api.NewItem;
import org.shpytchuk.telegramingest.channel.ChannelPost;
import org.shpytchuk.telegramingest.geo.ResolvedPlace;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Money;
import org.shpytchuk.telegramingest.model.SocialMedia;
import org.shpytchuk.telegramingest.telegram.IncomingPost;

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
                post.chatTitle());
    }
}
