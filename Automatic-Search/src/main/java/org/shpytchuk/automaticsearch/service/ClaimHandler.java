package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ClaimEvent;

public interface ClaimHandler {

    ItemKind kind();

    String verb();

    default String routingKey() {
        return kind().routingKey(verb());
    }

    void handle(ClaimEvent event);
}
