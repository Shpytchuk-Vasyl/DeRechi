package org.shpytchuk.adminapi.service;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.shpytchuk.adminapi.config.property.NotificationProperties;
import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.items.FoundItem;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.event.NotificationRequestedEvent;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.NotifyChannel;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.NotifiedMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@AllArgsConstructor
public class MatchNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationService.class);

    private static final String SUBJECT_KEY = "notification.match.subject";
    private static final String BODY_KEY = "notification.match.body";

    private final SimilarItemRepository similarItemRepository;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationProperties properties;
    private final MessageSource messages;
    private final Formats formats;


    @Transactional
    public NotifiedMatch notifyOwner(Long lostItemId, Long foundItemId, String actor, NotifyChannel channel) {
        SimilarItem match = similarItemRepository
                .findById(new SimilarItem.SimilarItemId(foundItemId, lostItemId))
                .orElseThrow(() -> new NotFoundException("entity.match", lostItemId + "/" + foundItemId));

        NotificationRequestedEvent event = getEvent(match, channel);

        try {
            rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), event);
        log.info("Сповістили власника загубленої {} про знайдену {} каналом {} (адмін {})",
                lostItemId, foundItemId, channel, actor);
        } catch (AmqpConnectException e) {
            log.error("Rabbit зараз не доступний.");
            throw e;
        }

        match.setNotifiedAt(Instant.now());
        match.setNotifiedBy(actor);
        similarItemRepository.save(match);

        return new NotifiedMatch(ItemMapper.toView(match.getLostItem()), ItemMapper.toCandidate(match));
    }

    private @NonNull NotificationRequestedEvent getEvent(SimilarItem match, NotifyChannel notifyChannel) {
        LostItem lost = match.getLostItem();
        FoundItem found = match.getFoundItem();
        ContactInfo owner = lost.getInfo();

        NotificationRequestedEvent event = new NotificationRequestedEvent(
                text(SUBJECT_KEY),
                text(BODY_KEY,
                        lost.getTitle(), formats.date(lost.getDate()),
                        found.getTitle(), found.getPlace().getName(), formats.date(found.getDate()),
                        formats.phone(found.getInfo().getPhone()), found.getInfo().getEmail()),
                notifyChannel.isNeedPhone() ? owner.getPhone() : null,
                notifyChannel.isNeedEmail() ? owner.getEmail() : null,
                socialMedias(notifyChannel),
                deduplicationKey(lost.getId(), found.getId()));
        return event;
    }

    private static SocialMediaEnum[] socialMedias(NotifyChannel channel) {
        SocialMediaEnum social = channel.social();
        return social == null ? new SocialMediaEnum[0] : new SocialMediaEnum[]{social};
    }

    private String text(String key, Object... arguments) {
        return messages.getMessage(key, arguments, LocaleContextHolder.getLocale());
    }

    private static String deduplicationKey(Long lostItemId, Long foundItemId) {
        return "match:%d:%d".formatted(lostItemId, foundItemId);
    }
}
