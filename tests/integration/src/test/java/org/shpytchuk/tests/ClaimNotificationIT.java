package org.shpytchuk.tests;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ClaimNotificationIT extends StackIT {

    @Test
    void aResponseToALostItemIsEmailedToItsAuthor() {
        Item item = createItem(Kind.LOST);
        Contact claimant = contact("claimant");

        Claim claim = claim(item, claimant);

        assertThat(claim.repeated()).isFalse();
        Mail mail = awaitOneMailTo(item.author().email());
        assertThat(mail.subject()).isEqualTo("DeRechi: вашу річ знайдено");
        assertThat(digits(mail.text())).contains(digits(claimant.phone()));
        assertThat(mail.text()).contains(claimant.email());
        assertThat(mailsTo(claimant.email())).isEmpty();
    }

    @Test
    void aResponseToAFoundItemIsEmailedToItsAuthor() {
        Item item = createItem(Kind.FOUND);
        Contact claimant = contact("claimant");

        claim(item, claimant);

        Mail mail = awaitOneMailTo(item.author().email());
        assertThat(mail.subject()).isEqualTo("DeRechi: знайдену вами річ шукає власник");
        assertThat(digits(mail.text())).contains(digits(claimant.phone()));
    }

    @Test
    void aRepeatedResponseIsNotEmailedAgain() {
        Item item = createItem(Kind.LOST);
        Contact claimant = contact("claimant");

        Claim first = claim(item, claimant);
        Claim second = claim(item, claimant);

        assertThat(second.repeated()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        awaitOneMailTo(item.author().email());
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(8))
                .until(() -> mailsTo(item.author().email()).size() == 1);
    }
}
