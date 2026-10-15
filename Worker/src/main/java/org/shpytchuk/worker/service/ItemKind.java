package org.shpytchuk.worker.service;


public enum ItemKind {

    LOST("lost"),
    FOUND("found");

    private final String segment;

    ItemKind(String segment) {
        this.segment = segment;
    }

    public String segment() {
        return segment;
    }

    public String noticeKey(String verb) {
        return "notice." + segment + "." + verb;
    }

    public String claimKey(String verb) {
        return "claim." + segment + "." + verb;
    }

    public ItemKind opposite() {
        return this == LOST ? FOUND : LOST;
    }
}
