package org.shpytchuk.worker.service;

import org.shpytchuk.worker.entity.core.ContactInfo.SocialMediaEnum;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.AllArgsConstructor;
import org.shpytchuk.worker.config.ClaimsProperties;
import org.shpytchuk.worker.entity.claim.Claim;
import org.shpytchuk.worker.entity.core.ContactInfo;
import org.shpytchuk.worker.entity.core.thing.Thing;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.shpytchuk.worker.language.PhoneLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ClaimNotifier {

    private static final Logger log = LoggerFactory.getLogger(ClaimNotifier.class);

    public static final String CREATED_ROUTING_KEY = "notification.claim.created";
    public static final String REMINDER_ROUTING_KEY = "notification.claim.reminder";

    private static final String MESSENGERS_NONE_KEY = "claim.messengers.none";
    private static final String AUTHOR_REMINDER_KEY = "claim.reminder.author";
    private static final String CLAIMANT_REMINDER_KEY = "claim.reminder.claimant";

    private static final PhoneNumberUtil PHONE_NUMBERS = PhoneNumberUtil.getInstance();

    private final RabbitTemplate rabbitTemplate;
    private final ClaimsProperties properties;
    private final MessageSource messages;

    public void notifyAuthor(ItemKind kind, Claim claim) {
        Thing item = claim.getItem();
        ContactInfo author = item.getInfo();
        ContactInfo claimant = claim.getContactInfo();
        Locale locale = PhoneLocales.of(author.getPhone());
        String key = "claim." + kind.segment();

        publish(CREATED_ROUTING_KEY, author,
                text(key + ".subject", locale),
                text(key + ".body", locale,
                        item.getTitle(),
                        formatPhone(claimant.getPhone()),
                        claimant.getEmail(),
                        messengers(claimant, locale),
                        noticeUrl(kind, item, locale)),
                "claim:%s:%d".formatted(kind.segment(), claim.getId()));
        log.info("Passed the contacts of {} claim {} to the author of item {}", kind, claim.getId(), item.getId());
    }

    public void remindAuthor(ItemKind kind, Claim claim) {
        remind(kind, claim, claim.getItem().getInfo(), AUTHOR_REMINDER_KEY,
                "claim:%s:%d:author-reminder".formatted(kind.segment(), claim.getId()));
    }

    public void remindClaimant(ItemKind kind, Claim claim) {
        remind(kind, claim, claim.getContactInfo(), CLAIMANT_REMINDER_KEY,
                "claim:%s:%d:claimant-reminder".formatted(kind.segment(), claim.getId()));
    }

    private void remind(ItemKind kind, Claim claim, ContactInfo recipient, String key, String deduplicationKey) {
        Locale locale = PhoneLocales.of(recipient.getPhone());
        String title = claim.getItem().getTitle();

        publish(REMINDER_ROUTING_KEY, recipient,
                text(key + ".subject", locale, title),
                text(key + ".body", locale, title, confirmUrl(claim, locale)),
                deduplicationKey);
        log.info("Sent {} for {} claim {}", key, kind, claim.getId());
    }

    private void publish(String routingKey, ContactInfo recipient, String subject, String body,
                         String deduplicationKey) {
        NotificationRequestedEvent event = new NotificationRequestedEvent(
                subject, body, recipient.getPhone(), recipient.getEmail(), new SocialMediaEnum[0], deduplicationKey);
        rabbitTemplate.convertAndSend(properties.exchange(), routingKey, event);
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
        };
    }

    private static String formatPhone(String phone) {
        if (phone == null || phone.isBlank()) {
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
}
