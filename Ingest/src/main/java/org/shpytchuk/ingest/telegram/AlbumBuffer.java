package org.shpytchuk.ingest.telegram;

import org.shpytchuk.ingest.source.IncomingPost;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class AlbumBuffer {

    private final Duration wait;
    private final TaskScheduler scheduler;
    private final Consumer<IncomingPost> sink;
    private final Map<Key, List<IncomingPost>> pending = new HashMap<>();

    AlbumBuffer(Duration wait, TaskScheduler scheduler, Consumer<IncomingPost> sink) {
        this.wait = wait;
        this.scheduler = scheduler;
        this.sink = sink;
    }

    void add(long mediaAlbumId, IncomingPost post) {
        if (mediaAlbumId == 0) {
            sink.accept(post);
            return;
        }
        Key key = new Key(MessageMapper.chatId(post), mediaAlbumId);
        int size;
        synchronized (this) {
            List<IncomingPost> parts = pending.computeIfAbsent(key, k -> new ArrayList<>());
            parts.add(post);
            size = parts.size();
        }
        scheduler.schedule(() -> flush(key, size), scheduler.getClock().instant().plus(wait));
    }

    private void flush(Key key, int size) {
        List<IncomingPost> parts;
        synchronized (this) {
            parts = pending.get(key);
            if (parts == null || parts.size() != size) {
                return;
            }
            pending.remove(key);
        }
        sink.accept(MessageMapper.merge(parts));
    }

    private record Key(long chatId, long mediaAlbumId) {
    }
}
