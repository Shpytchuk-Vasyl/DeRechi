package org.shpytchuk.ingest.model;

public enum ItemKind {
    LOST,
    FOUND;

    public String path() {
        return name().toLowerCase();
    }
}
