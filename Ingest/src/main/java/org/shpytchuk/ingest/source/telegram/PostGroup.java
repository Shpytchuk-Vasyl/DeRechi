package org.shpytchuk.ingest.source.telegram;

import org.shpytchuk.ingest.source.IncomingPost;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PostGroup {

    private final List<MessagePart> parts = new ArrayList<>();

    PostGroup(MessagePart first) {
        parts.add(first);
    }

    static List<IncomingPost> assemble(List<MessagePart> parts, Duration wait) {
        List<IncomingPost> posts = new ArrayList<>();
        Map<String, PostGroup> open = new HashMap<>();
        parts.stream()
                .sorted(Comparator.comparingLong(part -> MessageMapper.messageId(part.post())))
                .forEach(part -> {
                    PostGroup group = open.get(part.sender());
                    if (group != null && group.accepts(part, wait)) {
                        group.add(part);
                        return;
                    }
                    if (group != null) {
                        posts.add(group.post());
                    }
                    open.put(part.sender(), new PostGroup(part));
                });
        open.values().forEach(group -> posts.add(group.post()));
        return posts;
    }

    boolean accepts(MessagePart part, Duration wait) {
        if (part.albumId() != 0 && parts.stream().anyMatch(known -> known.albumId() == part.albumId())) {
            return true;
        }
        boolean secondText = part.hasText() && parts.stream().anyMatch(MessagePart::hasText);
        return !secondText && !part.post().postedAt().isAfter(latest().plus(wait));
    }

    void add(MessagePart part) {
        parts.add(part);
    }

    int size() {
        return parts.size();
    }

    IncomingPost post() {
        return parts.size() == 1 ? parts.getFirst().post()
                : MessageMapper.merge(parts.stream().map(MessagePart::post).toList());
    }

    private Instant latest() {
        return parts.stream().map(part -> part.post().postedAt()).max(Comparator.naturalOrder()).orElseThrow();
    }
}
