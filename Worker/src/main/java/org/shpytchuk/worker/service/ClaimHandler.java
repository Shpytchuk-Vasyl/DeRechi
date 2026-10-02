package org.shpytchuk.worker.service;

import org.shpytchuk.worker.event.ClaimEvent;

public interface ClaimHandler {

    ItemKind kind();

    String verb();

    default String routingKey() {
        return kind().routingKey(verb());
    }

    void handle(ClaimEvent event);
}
