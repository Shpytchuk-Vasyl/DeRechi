package org.shpytchuk.automaticsearch.service;

import org.shpytchuk.automaticsearch.event.ClaimEvent;

public interface ClaimHandler {

    String routingKey();

    void handle(ClaimEvent event);
}
