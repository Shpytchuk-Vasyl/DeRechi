package org.shpytchuk.worker.handler;

import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.service.ItemKind;

public interface ClaimHandler {

    ItemKind kind();

    String routingKey();

    void handle(ClaimEvent event);
}
