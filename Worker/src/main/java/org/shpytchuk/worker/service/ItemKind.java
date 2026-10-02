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

    public String routingKey(String verb) {
        return "item." + segment + "." + verb;
    }
}
