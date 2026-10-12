package org.shpytchuk.ingest.telegram;

import org.shpytchuk.ingest.source.IncomingPost;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class PostBuffer {

    private final Duration wait;
    private final TaskScheduler scheduler;
    private final Consumer<IncomingPost> sink;
    private final Map<Key, PostGroup> pending = new HashMap<>();

    PostBuffer(Duration wait, TaskScheduler scheduler, Consumer<IncomingPost> sink) {
        this.wait = wait;
        this.scheduler = scheduler;
        this.sink = sink;
    }

    void add(MessagePart part) {
        Key key = new Key(MessageMapper.chatId(part.post()), part.sender());
        PostGroup complete = null;
        PostGroup group;
        int size;
        synchronized (this) {
            group = pending.get(key);
            if (group != null && group.accepts(part, wait)) {
                group.add(part);
            } else {
                complete = group;
                group = new PostGroup(part);
                pending.put(key, group);
            }
            size = group.size();
        }
        if (complete != null) {
            sink.accept(complete.post());
        }
        PostGroup scheduled = group;
        scheduler.schedule(() -> flush(key, scheduled, size), scheduler.getClock().instant().plus(wait));
    }

    private void flush(Key key, PostGroup group, int size) {
        synchronized (this) {
            if (pending.get(key) != group || group.size() != size) {
                return;
            }
            pending.remove(key);
        }
        sink.accept(group.post());
    }

    private record Key(long chatId, String sender) {
    }
}
