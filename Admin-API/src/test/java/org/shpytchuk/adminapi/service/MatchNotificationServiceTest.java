package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.property.NotificationProperties;
import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.items.FoundItem;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.Place;
import org.shpytchuk.adminapi.entity.ThingCategory;
import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.event.NotificationRequestedEvent;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.NotifyChannel;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.NotifiedMatch;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchNotificationServiceTest {

    private static final NotificationProperties PROPERTIES =
            new NotificationProperties("derechi.notifications", "notification.match.found");

    private SimilarItemRepository repository;
    private RabbitTemplate rabbitTemplate;
    private MatchNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(SimilarItemRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new MatchNotificationService(repository, rabbitTemplate, PROPERTIES);
    }

    @Test
    void publishesNotificationToTheOwnerOfTheLostItem() {
        SimilarItem match = match();
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        NotifiedMatch notified = service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL);

        var event = forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("derechi.notifications"), eq("notification.match.found"), event.capture());

        assertThat(event.getValue().email()).isEqualTo("owner@example.com");
        assertThat(event.getValue().phone()).isEqualTo("+380671234567");
        assertThat(event.getValue().socialMedias()).containsExactly(SocialMediaEnum.TELEGRAM);
        assertThat(event.getValue().deduplicationKey()).isEqualTo("match:1:2");
        assertThat(event.getValue().message()).contains("Загублений рюкзак", "Знайдений рюкзак", "Метро Хрещатик");

        assertThat(match.getNotifiedAt()).isNotNull();
        assertThat(match.getNotifiedBy()).isEqualTo("admin@derechi.local");
        assertThat(notified.lost().id()).isEqualTo(1L);
        assertThat(notified.candidate().found().id()).isEqualTo(2L);
        assertThat(notified.candidate().notifiedAt()).isEqualTo(match.getNotifiedAt());
        assertThat(notified.candidate().notifiedBy()).isEqualTo("admin@derechi.local");
        verify(repository).save(match);
    }

    @Test
    void failsWhenMatchDoesNotExist() {
        when(repository.findById(any(SimilarItem.SimilarItemId.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL))
                .isInstanceOf(NotFoundException.class);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private static SimilarItem match() {
        ContactInfo owner = new ContactInfo();
        owner.setPhone("+380671234567");
        owner.setEmail("owner@example.com");
        owner.setSocialMedias(new SocialMediaEnum[]{SocialMediaEnum.TELEGRAM});

        ThingCategory category = new ThingCategory();
        category.setId(7L);
        category.setKey("BAGS");

        Place place = new Place();
        place.setGooglePlaceId("place-1");
        place.setName("Метро Хрещатик");

        LostItem lost = new LostItem();
        lost.setId(1L);
        lost.setTitle("Загублений рюкзак");
        lost.setDate(LocalDate.of(2026, 9, 1));
        lost.setInfo(owner);
        lost.setPlace(place);
        lost.setCategory(category);

        FoundItem found = new FoundItem();
        found.setId(2L);
        found.setTitle("Знайдений рюкзак");
        found.setDate(LocalDate.of(2026, 9, 2));
        found.setPlace(place);
        found.setCategory(category);

        ContactInfo finder = new ContactInfo();
        finder.setPhone("+380509876543");
        finder.setEmail("finder@example.com");
        finder.setSocialMedias(new SocialMediaEnum[0]);
        found.setInfo(finder);

        SimilarItem match = new SimilarItem();
        match.setId(new SimilarItem.SimilarItemId(2L, 1L));
        match.setLostItem(lost);
        match.setFoundItem(found);
        match.setMatchOrder(0.42);
        return match;
    }
}
