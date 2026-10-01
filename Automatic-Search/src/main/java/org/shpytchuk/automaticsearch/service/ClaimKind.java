package org.shpytchuk.automaticsearch.service;

public enum ClaimKind {

    LOST("lost"),
    FOUND("found");

    private final String segment;

    ClaimKind(String segment) {
        this.segment = segment;
    }

    public String segment() {
        return segment;
    }

    public String routingKey(String verb) {
        return "item." + segment + "." + verb;
    }
}
