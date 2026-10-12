package org.shpytchuk.ingest.support;

import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;
import org.shpytchuk.ingest.telegram.TelegramDetails;
import org.shpytchuk.ingest.telegram.TelegramPostSource;

import java.time.Instant;
import java.util.List;

public final class Posts {

    public static final long CHAT_ID = -100L;
    public static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");

    private Posts() {
    }

    public static IncomingPost telegram(long messageId, String text, List<PhotoRef> photos, Instant postedAt) {
        PostKey key = new PostKey(TelegramPostSource.NAME, Long.toString(CHAT_ID), Long.toString(messageId));
        return new IncomingPost(key, "Загублено Львів", "https://t.me/zagubleno911/" + (messageId >> 20), postedAt,
                text, photos, LVIV,
                new TelegramDetails(CHAT_ID, messageId, "zagubleno911", null, List.of(), List.of()));
    }
}
