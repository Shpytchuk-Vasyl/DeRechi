package org.shpytchuk.worker.service;

import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.worker.config.ClaimsProperties;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.detail.ContactInfo;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.shpytchuk.worker.language.PhoneLocales;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class ClaimNotifier {

    public static final String CREATED_ROUTING_KEY = "notification.claim.created";
    public static final String REMINDER_ROUTING_KEY = "notification.claim.reminder";
    public static final String UNLOCKED_ROUTING_KEY = "notification.claim.unlocked";

    private static final String MESSENGERS_NONE_KEY = "claim.messengers.none";

    static final int TITLE_MAX_LENGTH = 15;
    private static final String TITLE_ELLIPSIS = "...";

    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();

    private final RabbitTemplate rabbitTemplate;
    private final ClaimsProperties properties;
    private final MessageSource messages;

    public void notifyAuthor(ItemKind kind, Claim claim) {
        ContactInfo claimant = claim.getContactInfo();
        send(ClaimMessage.CREATED, kind, claim, (notice, title, locale) -> new Object[]{
                title, formatPhone(claimant.getPhone()), claimant.getEmail(),
                messengers(claimant, locale), noticeUrl(kind, notice, locale)});
    }

    public void remindAuthor(ItemKind kind, Claim claim) {
        send(ClaimMessage.AUTHOR_REMINDER, kind, claim,
                (notice, title, locale) -> new Object[]{title, confirmUrl(claim, locale)});
    }

    public void remindClaimant(ItemKind kind, Claim claim) {
        send(ClaimMessage.CLAIMANT_REMINDER, kind, claim,
                (notice, title, locale) -> new Object[]{title, confirmUrl(claim, locale)});
    }

    public boolean sendAuthorContacts(ItemKind kind, Claim claim) {
        String authorPhone = claim.notice().getInfo().getPhone();
        if (!StringUtils.hasText(authorPhone)) {
            log.warn("The author of {} item {} has no phone, nothing to send for claim {}",
                    kind, claim.notice().getId(), claim.getId());
            return false;
        }
        send(ClaimMessage.UNLOCKED, kind, claim,
                (notice, title, locale) -> new Object[]{title, formatPhone(authorPhone)});
        return true;
    }

    /** Renders the message in the language of the recipient's phone number and publishes it. */
    private void send(ClaimMessage message, ItemKind kind, Claim claim, BodyArguments body) {
        Thing notice = claim.notice();
        ContactInfo recipient = message.recipient().of(claim);
        Locale locale = PhoneLocales.of(recipient.getPhone());
        String title = shortTitle(notice.getTitle());
        String key = message.textKey(kind);

        // Every subject gets the title; the CREATED subjects have no placeholder and MessageFormat drops the spare argument.
        NotificationRequestedEvent event = new NotificationRequestedEvent(
                text(key + ".subject", locale, title),
                text(key + ".body", locale, body.of(notice, title, locale)),
                recipient.getPhone(), recipient.getEmail(), new SocialMediaEnum[0],
                message.deduplicationKey(kind, claim));
        rabbitTemplate.convertAndSend(properties.exchange(), message.routingKey(), event);
        log.info("Sent {} for {} claim {} on item {} to the {}",
                key, kind, claim.getId(), notice.getId(), message.recipient());
    }

    private String noticeUrl(ItemKind kind, Thing item, Locale locale) {
        return "%s/%s/%s/%d".formatted(properties.siteUrl(), locale.getLanguage(), kind.segment(), item.getId());
    }

    private String confirmUrl(Claim claim, Locale locale) {
        return "%s/%s/claims/%s".formatted(properties.siteUrl(), locale.getLanguage(), claim.getToken());
    }

    private String messengers(ContactInfo contact, Locale locale) {
        SocialMediaEnum[] socialMedias = contact.getSocialMedias();
        if (socialMedias == null || socialMedias.length == 0) {
            return text(MESSENGERS_NONE_KEY, locale);
        }
        return Arrays.stream(socialMedias)
                .map(ClaimNotifier::messengerName)
                .collect(Collectors.joining(", "));
    }

    private static String messengerName(SocialMediaEnum socialMedia) {
        return switch (socialMedia) {
            case TELEGRAM -> "Telegram";
            case VIBER -> "Viber";
            case WHATSAPP -> "WhatsApp";
            case FACEBOOK -> "Facebook";
        };
    }

    static String shortTitle(String title) {
        if (title == null || title.codePointCount(0, title.length()) <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_MAX_LENGTH)).strip() + TITLE_ELLIPSIS;
    }

    private static String formatPhone(@Nullable String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        try {
            return PHONE_NUMBERS.format(PHONE_NUMBERS.parse(phone, null), PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException unparseable) {
            return phone;
        }
    }

    private String text(String key, Locale locale, Object... arguments) {
        return messages.getMessage(key, arguments, locale);
    }

    @FunctionalInterface
    private interface BodyArguments {

        Object[] of(Thing notice, String title, Locale locale);
    }
}
