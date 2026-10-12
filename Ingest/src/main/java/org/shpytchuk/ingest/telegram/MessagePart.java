package org.shpytchuk.ingest.telegram;

import org.shpytchuk.ingest.source.IncomingPost;

record MessagePart(IncomingPost post, String sender, long albumId) {

    boolean hasText() {
        return !post.text().isBlank();
    }
}
