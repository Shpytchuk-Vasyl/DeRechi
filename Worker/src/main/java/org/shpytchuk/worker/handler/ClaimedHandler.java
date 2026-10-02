package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AllArgsConstructor
public class ClaimedHandler implements ClaimHandler {

    private static final Logger log = LoggerFactory.getLogger(ClaimedHandler.class);

    private static final String VERB = "claimed";

    private final ItemKind kind;
    private final ClaimRepositories repositories;
    private final ClaimNotifier notifier;

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
        repositories.of(kind).findWithDetailsById(event.getId())
                .filter(Claim::isLive)
                .ifPresentOrElse(
                claim -> notifier.notifyAuthor(kind, claim),
                () -> log.info("{} claim {} is gone (the notice is closed), nothing to send", kind, event.getId()));
    }
}
