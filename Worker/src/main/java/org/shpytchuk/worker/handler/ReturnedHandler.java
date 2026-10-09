package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;

import java.util.Optional;

@AllArgsConstructor
@Slf4j
public class ReturnedHandler implements ClaimHandler {

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
