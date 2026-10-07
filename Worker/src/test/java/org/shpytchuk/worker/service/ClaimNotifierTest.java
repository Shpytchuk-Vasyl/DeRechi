package org.shpytchuk.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.shpytchuk.worker.support.Fixtures.PROPERTIES;
import static org.shpytchuk.worker.support.Fixtures.foundClaim;
import static org.shpytchuk.worker.support.Fixtures.lostClaim;
import static org.shpytchuk.worker.support.Fixtures.messages;

class ClaimNotifierTest {

    private RabbitTemplate rabbitTemplate;
    private ClaimNotifier notifier;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        notifier = new ClaimNotifier(rabbitTemplate, PROPERTIES, messages());
    }

    @Test
    void passesTheClaimantContactsToTheAuthorInTheAuthorsLanguage() {
        LostItemClaim claim = lostClaim("+380671234567", SocialMediaEnum.TELEGRAM, SocialMediaEnum.WHATSAPP);

        notifier.notifyAuthor(ItemKind.LOST, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.CREATED_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: вашу річ знайдено");
        assertThat(event.message())
                .startsWith("Вітаємо!\n\nЛюдина повідомляє, що знайшла вашу річ «Чорний рюкзак»")
                .contains("Контакти:\n  Телефон: +380 50 987 6543\n  Пошта: finder@example.com\n"
                                + "  Месенджери: Telegram, WhatsApp\n",
                        "Оголошення: http://localhost:3000/uk/lost/1")
                .endsWith("\n\n– Команда DeRechi")
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
                .startsWith("Hello,\n\nSomeone says the item “Black backpack” you found is theirs")
                .contains("  Messengers: none\n", "Notice: http://localhost:3000/en/found/2")
                .endsWith("\n\n– The DeRechi team");
        assertThat(event.phone()).isEqualTo("+16502530000");
        assertThat(event.deduplicationKey()).isEqualTo("claim:found:43");
    }

    @ParameterizedTest(name = "{0} notice, {2}")
    @CsvSource({
            "LOST, +380671234567, uk", "LOST, +48512345678, pl", "LOST, +4930123456, de",
            "LOST, +33123456789, fr", "LOST, +16502530000, en",
            "FOUND, +380671234567, uk", "FOUND, +48512345678, pl", "FOUND, +4930123456, de",
            "FOUND, +33123456789, fr", "FOUND, +16502530000, en"})
    void writesTheAuthorTheSameLetterInEveryLanguage(ItemKind kind, String authorPhone, String language) {
        notifier.notifyAuthor(kind, kind == ItemKind.LOST ? lostClaim(authorPhone) : foundClaim(authorPhone));

        String message = published(ClaimNotifier.CREATED_ROUTING_KEY).message();
        List<String> lines = message.lines().toList();

        // greeting, lead, three indented contact lines, the notice link, the safety advice (lost only), the signature
        assertThat(lines).as(message).hasSize(kind == ItemKind.LOST ? 14 : 12);
        assertThat(List.of(lines.get(1), lines.get(3), lines.get(8), lines.get(10), lines.get(lines.size() - 2)))
                .as(message).allMatch(String::isEmpty);
        assertThat(lines.subList(5, 8)).as(message).allMatch(line -> line.startsWith("  ") && line.contains(":"));
        assertThat(lines.get(9)).as(message)
                .endsWith("http://localhost:3000/%s/%s/%d".formatted(language, kind.segment(), kind == ItemKind.LOST ? 1 : 2));
        assertThat(lines.getLast()).as(message).startsWith("– ").contains("DeRechi");
        assertThat(message).as(message).doesNotContain("{", "}", "''");
    }

    @Test
    void cutsALongTitleSoTheSmsStaysShort() {
        FoundItemClaim claim = foundClaim("+380671234567");
        claim.getItem().setTitle("Чорний шкіряний рюкзак з ноутбуком");

        notifier.notifyAuthor(ItemKind.FOUND, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.CREATED_ROUTING_KEY);
        assertThat(event.message())
                .contains("«Чорний шкіряний...»")
                .doesNotContain("рюкзак з ноутбуком", "розпитайте про деталі");
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Чорний рюкзак            | Чорний рюкзак",
            "Ключі від квартир        | Ключі від кварт...",
            "Ключі від кварти         | Ключі від кварт...",
            "Ключі від кварт          | Ключі від кварт",
            "Синій гаманець і картки  | Синій гаманець...",
            "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀 | 😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀..."})
    void cutsTitlesLongerThanFifteenCharacters(String title, String expected) {
        assertThat(ClaimNotifier.shortTitle(title)).isEqualTo(expected);
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

    @Test
    void sendsTheAuthorPhoneNumberToTheClaimantInTheClaimantsLanguage() {
        LostItemClaim claim = lostClaim("+48512345678");

        notifier.sendAuthorContacts(ItemKind.LOST, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.UNLOCKED_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: номер автора оголошення «Чорний рюкзак»");
        assertThat(event.message())
                .startsWith("Дякуємо, що підтримуєте DeRechi.")
                .contains("«Чорний рюкзак»", "+48 512 345 678")
                .doesNotContain("{0}", "{1}", "owner@example.com", "месенджери", "+380 50 987 6543", "finder@example.com");
        assertThat(event.phone()).isEqualTo("+380509876543");
        assertThat(event.email()).isEqualTo("finder@example.com");
        assertThat(event.socialMedias()).isEmpty();
        assertThat(event.deduplicationKey()).isEqualTo("claim:lost:42:unlocked");
    }

    @Test
    void sendsOnlyTheAuthorPhoneNumberInEnglishToAClaimantWithAPhoneOutsideTheSupportedCountries() {
        FoundItemClaim claim = foundClaim("+380671234567");
        claim.getItem().getInfo().setSocialMedias(new SocialMediaEnum[]{SocialMediaEnum.VIBER, SocialMediaEnum.TELEGRAM});

        notifier.sendAuthorContacts(ItemKind.FOUND, claim);

        NotificationRequestedEvent event = published(ClaimNotifier.UNLOCKED_ROUTING_KEY);
        assertThat(event.subject()).isEqualTo("DeRechi: the author's phone number for “Black backpack”");
        assertThat(event.message())
                .startsWith("Thank you for supporting DeRechi. The phone number of the person behind the notice “Black backpack”: +380 67 123 4567")
                .contains("never pay anything in advance")
                .doesNotContain("finder@example.com", "Viber", "Telegram", "messengers");
        assertThat(event.phone()).isEqualTo("+12125550123");
        assertThat(event.email()).isEqualTo("owner@example.com");
        assertThat(event.deduplicationKey()).isEqualTo("claim:found:43:unlocked");
    }

    private NotificationRequestedEvent published(String routingKey) {
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("derechi.notifications"), eq(routingKey), event.capture());
        return event.getValue();
    }

}
