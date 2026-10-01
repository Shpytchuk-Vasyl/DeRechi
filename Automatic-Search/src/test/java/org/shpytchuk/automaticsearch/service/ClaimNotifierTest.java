package org.shpytchuk.automaticsearch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.automaticsearch.config.ClaimsProperties;
import org.shpytchuk.automaticsearch.entity.ContactInfo;
import org.shpytchuk.automaticsearch.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.automaticsearch.entity.FoundItem;
import org.shpytchuk.automaticsearch.entity.FoundItemClaim;
import org.shpytchuk.automaticsearch.entity.LostItem;
import org.shpytchuk.automaticsearch.entity.LostItemClaim;
import org.shpytchuk.automaticsearch.event.NotificationRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ClaimNotifierTest {

    static final ClaimsProperties PROPERTIES = new ClaimsProperties(
            "automatic-search.claims", "derechi.notifications", "http://localhost:3000/",
            Duration.ofMinutes(10), Duration.ofDays(1), Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(365));

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

        notifier.notifyAuthor(ClaimKind.LOST, claim);

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

        notifier.notifyAuthor(ClaimKind.FOUND, claim);

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

        notifier.remindAuthor(ClaimKind.LOST, claim);

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

        notifier.remindClaimant(ClaimKind.LOST, claim);

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

    static LostItemClaim lostClaim(String authorPhone, SocialMediaEnum... claimantMessengers) {
        LostItem item = new LostItem();
        item.setId(1L);
        item.setTitle("Чорний рюкзак");
        item.setInfo(contact(authorPhone, "owner@example.com"));

        LostItemClaim claim = new LostItemClaim();
        claim.setId(42L);
        claim.setItem(item);
        claim.setContactInfo(contact("+380509876543", "finder@example.com", claimantMessengers));
        claim.setToken("6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11");
        claim.setCreatedAt(Instant.parse("2026-09-01T10:00:00Z"));
        return claim;
    }

    static FoundItemClaim foundClaim(String authorPhone) {
        FoundItem item = new FoundItem();
        item.setId(2L);
        item.setTitle("Black backpack");
        item.setInfo(contact(authorPhone, "finder@example.com"));

        FoundItemClaim claim = new FoundItemClaim();
        claim.setId(43L);
        claim.setItem(item);
        claim.setContactInfo(contact("+12125550123", "owner@example.com"));
        claim.setToken("0b9a3c1d-5e2f-4a6b-8c7d-9e0f1a2b3c4d");
        claim.setCreatedAt(Instant.parse("2026-09-01T10:00:00Z"));
        return claim;
    }

    private static ContactInfo contact(String phone, String email, SocialMediaEnum... socialMedias) {
        ContactInfo contact = new ContactInfo();
        contact.setPhone(phone);
        contact.setEmail(email);
        contact.setSocialMedias(socialMedias);
        return contact;
    }
}
