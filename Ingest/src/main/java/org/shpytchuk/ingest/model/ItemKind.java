package org.shpytchuk.ingest.model;

public enum ItemKind {
    LOST,
    FOUND;

    public String path() {
        return name().toLowerCase();
    }

    public String noticeKey(String verb) {
        return "notice." + path() + "." + verb;
    }
}
