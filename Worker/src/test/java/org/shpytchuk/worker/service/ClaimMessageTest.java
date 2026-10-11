package org.shpytchuk.worker.service;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.service.ClaimMessage.Recipient;
import org.shpytchuk.worker.support.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.worker.service.ClaimMessage.AUTHOR_REMINDER;
import static org.shpytchuk.worker.service.ClaimMessage.CLAIMANT_REMINDER;
import static org.shpytchuk.worker.service.ClaimMessage.CREATED;
import static org.shpytchuk.worker.service.ClaimMessage.UNLOCKED;

class ClaimMessageTest {

    @Test
    void goesOutUnderTheRoutingKeyOfItsReason() {
        assertThat(CREATED.routingKey()).isEqualTo("notification.claim.created");
        assertThat(AUTHOR_REMINDER.routingKey()).isEqualTo("notification.claim.reminder");
        assertThat(CLAIMANT_REMINDER.routingKey()).isEqualTo("notification.claim.reminder");
        assertThat(UNLOCKED.routingKey()).isEqualTo("notification.claim.unlocked");
    }

    @Test
    void onlyTheFirstMessageHasATextPerKind() {
        assertThat(CREATED.textKey(ItemKind.LOST)).isEqualTo("claim.lost");
        assertThat(CREATED.textKey(ItemKind.FOUND)).isEqualTo("claim.found");
        for (ItemKind kind : ItemKind.values()) {
            assertThat(AUTHOR_REMINDER.textKey(kind)).isEqualTo("claim.reminder.author");
            assertThat(CLAIMANT_REMINDER.textKey(kind)).isEqualTo("claim.reminder.claimant");
            assertThat(UNLOCKED.textKey(kind)).isEqualTo("claim.unlocked");
        }
    }

    @Test
    void isDeduplicatedPerClaimAndPerReason() {
        LostItemClaim lost = Fixtures.lostClaim("+380671234567");

        assertThat(CREATED.deduplicationKey(ItemKind.LOST, lost)).isEqualTo("claim:lost:42");
        assertThat(AUTHOR_REMINDER.deduplicationKey(ItemKind.LOST, lost)).isEqualTo("claim:lost:42:author-reminder");
        assertThat(CLAIMANT_REMINDER.deduplicationKey(ItemKind.LOST, lost)).isEqualTo("claim:lost:42:claimant-reminder");
        assertThat(UNLOCKED.deduplicationKey(ItemKind.FOUND, Fixtures.foundClaim("+380671234567")))
                .isEqualTo("claim:found:43:unlocked");
    }

    @Test
    void theAuthorIsTheContactOfTheNoticeLiveOrArchivedAndTheClaimantTheContactOfTheClaim() {
        LostItemClaim claim = Fixtures.lostClaim("+380671234567");
        assertThat(CREATED.recipient()).isEqualTo(Recipient.AUTHOR);
        assertThat(AUTHOR_REMINDER.recipient()).isEqualTo(Recipient.AUTHOR);
        assertThat(CLAIMANT_REMINDER.recipient()).isEqualTo(Recipient.CLAIMANT);
        assertThat(UNLOCKED.recipient()).isEqualTo(Recipient.CLAIMANT);

        assertThat(Recipient.AUTHOR.of(claim)).isSameAs(claim.getItem().getInfo());
        assertThat(Recipient.CLAIMANT.of(claim)).isSameAs(claim.getContactInfo());

        LostItemHistory history = new LostItemHistory();
        history.setInfo(Fixtures.contact("+48512345678", "archived-author@example.com"));
        claim.moveToArchive(history);
        assertThat(Recipient.AUTHOR.of(claim)).isSameAs(history.getInfo());
    }
}
