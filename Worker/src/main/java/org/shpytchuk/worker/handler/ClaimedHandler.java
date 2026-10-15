package org.shpytchuk.worker.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemKinds;

@AllArgsConstructor
@Slf4j
public class ClaimedHandler implements ClaimHandler {

    private static final String VERB = "claimed";

    private final ItemKind kind;
    private final ItemKinds kinds;
    private final ClaimNotifier notifier;

    @Override
    public ItemKind kind() {
        return kind;
    }

    @Override
    public String routingKey() {
        return kind.claimKey(VERB);
    }

    @Override
    public void handle(ClaimEvent event) {
        kinds.claims(kind).findWithDetailsById(event.getId())
                .filter(Claim::isLive)
                .ifPresentOrElse(
                claim -> notifier.notifyAuthor(kind, claim),
                () -> log.info("{} claim {} is gone (the notice is closed), nothing to send", kind, event.getId()));
    }
}
