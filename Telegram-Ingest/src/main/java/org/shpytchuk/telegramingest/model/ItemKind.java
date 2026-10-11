package org.shpytchuk.telegramingest.model;

public enum ItemKind {
    LOST,
    FOUND;

    public String path() {
        return name().toLowerCase();
    }
}
