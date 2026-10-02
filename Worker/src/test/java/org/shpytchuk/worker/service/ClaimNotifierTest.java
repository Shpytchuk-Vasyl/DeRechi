package org.shpytchuk.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.shpytchuk.worker.support.Fixtures.PROPERTIES;
import static org.shpytchuk.worker.support.Fixtures.foundClaim;
import static org.shpytchuk.worker.support.Fixtures.lostClaim;

class ClaimNotifierTest {

    private RabbitTemplate rabbitTemplate;
    private ClaimNotifier notifier;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        notifier = new ClaimNotifier(rabbitTemplate, PROPERTIES, messages());
    }

    /** Same settings as spring.messages; without the system-locale switch "en" would land on the machine's language. */
    private static MessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    @Test
    void passesTheClaimantContactsToTheAuthorInTheAuthorsLanguage() {
        LostItemClaim claim = lostClaim("+380671234567", SocialMediaEnum.TELEGRAM, SocialMediaEnum.WHATSAPP);

        notifier.notifyAuthor(ItemKind.LOST, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.CREATED_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: вашу річ знайдено");
        assertThat(event.message())
                .contains("«Чорний рюкзак»", "+380 50 987 6543", "finder@example.com", "Telegram, WhatsApp",
                        "http://localhost:3000/uk/lost/1")
                .doesNotContain("{0}", "{4}", "claim.lost.body");
        assertThat(event.phone()).isEqualTo("+380671234567");
        assertThat(event.email()).isEqualTo("owner@example.com");
        assertThat(event.socialMedias()).isEmpty();
        assertThat(event.deduplicationKey()).isEqualTo("claim:lost:42");
    }

    @Test
    void writesInEnglishToAnAuthorWithAPhoneOutsideTheSupportedCountries() {
        FoundItemClaim claim = foundClaim("+16502530000");

        notifier.notifyAuthor(ItemKind.FOUND, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.CREATED_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: the owner of the item you found showed up");
        assertThat(event.message())
                .startsWith("Someone says the item “Black backpack” you found is theirs")
                .contains("messengers: none", "http://localhost:3000/en/found/2");
        assertThat(event.phone()).isEqualTo("+16502530000");
        assertThat(event.deduplicationKey()).isEqualTo("claim:found:43");
    }

    @Test
    void remindsTheAuthorWithTheConfirmLink() {
        LostItemClaim claim = lostClaim("+48512345678");

        notifier.remindAuthor(ItemKind.LOST, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.REMINDER_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: czy rzecz „Чорний рюкзак” wróciła do właściciela?");
        assertThat(event.message()).contains("http://localhost:3000/pl/claims/" + claim.getToken());
        assertThat(event.phone()).isEqualTo("+48512345678");
        assertThat(event.deduplicationKey()).isEqualTo("claim:lost:42:author-reminder");
    }

    @Test
    void remindsTheClaimantInTheClaimantsLanguage() {
        LostItemClaim claim = lostClaim("+380671234567");
        claim.getContactInfo().setPhone("+33123456789");

        notifier.remindClaimant(ItemKind.LOST, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.REMINDER_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi : l'objet « Чорний рюкзак » a-t-il été rendu ?");
        assertThat(event.message()).contains("http://localhost:3000/fr/claims/" + claim.getToken());
        assertThat(event.phone()).isEqualTo("+33123456789");
        assertThat(event.email()).isEqualTo("finder@example.com");
        assertThat(event.deduplicationKey()).isEqualTo("claim:lost:42:claimant-reminder");
    }

    private NotificationRequestedEvent published(String routingKey) {
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("derechi.notifications"), eq(routingKey), event.capture());
        return event.getValue();
    }

}
