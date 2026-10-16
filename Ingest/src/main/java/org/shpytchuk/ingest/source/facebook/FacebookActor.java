package org.shpytchuk.ingest.source.facebook;

public record FacebookActor(String name, String profileUrl) {

    @Override
    public String toString() {
        return "FacebookActor";
    }
}
