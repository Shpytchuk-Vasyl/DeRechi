package org.shpytchuk.worker.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@AllArgsConstructor
public class ReturnedHandler implements ClaimHandler {

    private static final Logger log = LoggerFactory.getLogger(ReturnedHandler.class);

    private static final String VERB = "returned";

    private final ItemKind kind;
    private final ClaimRepositories repositories;
    private final ItemArchiver archiver;

    @Override
    public ItemKind kind() {
        return kind;
    }

    @Override
    public String verb() {
        return VERB;
    }

    @Override
    public void handle(ClaimEvent event) {
        Optional<Long> itemId = repositories.of(kind).findWithDetailsById(event.getId())
                .filter(Claim::isLive)
                .map(claim -> claim.getItem().getId());
        if (itemId.isEmpty()) {
            log.info("{} claim {} is gone or already archived, the notice is closed", kind, event.getId());
            return;
        }
        archiver.archive(kind, itemId.get());
    }
}
