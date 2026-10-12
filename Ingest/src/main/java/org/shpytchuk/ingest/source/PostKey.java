package org.shpytchuk.ingest.source;

public record PostKey(String source, String channel, String id) {

    @Override
    public String toString() {
        return source + "/" + channel + "/" + id;
    }
}
