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
import org.shpytchuk.adminapi.view.NotifiedMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@AllArgsConstructor
public class MatchNotificationService {

    private static final Logger log = LoggerFactory.getLogger(MatchNotificationService.class);

    private static final String SUBJECT = "DeRechi: можливо, вашу річ знайшли";
    private static final String MESSAGE = """
            Вітаємо! Щодо вашої заявки «%s» (%s) знайшовся схожий предмет: «%s», %s, %s.
            Зайдіть у DeRechi, щоб переглянути знахідку та звʼязатися з тим, хто її знайшов.""";

    private final SimilarItemRepository similarItemRepository;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationProperties properties;


    @Transactional
    public NotifiedMatch notifyOwner(Long lostItemId, Long foundItemId, String actor, NotifyChannel channel) {
        SimilarItem match = similarItemRepository
                .findById(new SimilarItem.SimilarItemId(foundItemId, lostItemId))
                .orElseThrow(() -> new NotFoundException("entity.match", lostItemId + "/" + foundItemId));

        NotificationRequestedEvent event = getEvent(lostItemId, foundItemId, match);

        rabbitTemplate.convertAndSend(properties.exchange(), properties.routingKey(), event);
        log.info("Сповістили власника загубленої {} про знайдену {} каналом {} (адмін {})",
                lostItemId, foundItemId, channel, actor);

        match.setNotifiedAt(Instant.now());
        match.setNotifiedBy(actor);
        similarItemRepository.save(match);

        return new NotifiedMatch(ItemMapper.toView(match.getLostItem()), ItemMapper.toCandidate(match));
    }

    private static @NonNull NotificationRequestedEvent getEvent(Long lostItemId, Long foundItemId, SimilarItem match) {
        LostItem lost = match.getLostItem();
        FoundItem found = match.getFoundItem();
        ContactInfo owner = lost.getInfo();

        NotificationRequestedEvent event = new NotificationRequestedEvent(
                SUBJECT,
                MESSAGE.formatted(
                        lost.getTitle(), lost.getDate(),
                        found.getTitle(), found.getPlace().getName(), found.getDate()),
                owner.getPhone(),
                owner.getEmail(),
                owner.getSocialMedias(),
                deduplicationKey(lostItemId, foundItemId));
        return event;
    }

    private static String deduplicationKey(Long lostItemId, Long foundItemId) {
        return "match:%d:%d".formatted(lostItemId, foundItemId);
    }
}
