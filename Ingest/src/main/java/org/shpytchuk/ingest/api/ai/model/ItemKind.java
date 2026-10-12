package org.shpytchuk.ingest.api.ai.model;

public enum ItemKind {
    LOST,
    FOUND;

    public String path() {
        return name().toLowerCase();
    }
}
