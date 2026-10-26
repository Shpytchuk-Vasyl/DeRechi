package org.shpytchuk.adminapi.service.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.property.NotificationProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.entity.matching.SimilarItem;
import org.shpytchuk.adminapi.event.NotificationRequestedEvent;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.exception.OwnerWithoutPhoneException;
import org.shpytchuk.adminapi.form.NotifyChannel;
import org.shpytchuk.adminapi.repository.matching.SimilarItemRepository;
import org.shpytchuk.adminapi.view.Formats;
import org.shpytchuk.adminapi.view.matching.NotifiedMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchNotificationServiceTest {

    private static final NotificationProperties PROPERTIES =
            new NotificationProperties("notification.exchange", "notification.match.found");

    private SimilarItemRepository repository;
    private RabbitTemplate rabbitTemplate;
    private MatchNotificationService service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.of("uk", "UA"));
        repository = mock(SimilarItemRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new MatchNotificationService(repository, rabbitTemplate, PROPERTIES, messages(), new Formats());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static MessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return source;
    }

    @Test
    void publishesNotificationToTheOwnerOfTheLostItem() {
        SimilarItem match = match();
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        NotifiedMatch notified = service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL);

        var event = forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("notification.exchange"), eq("notification.match.found"), event.capture());

        assertThat(event.getValue().email()).isEqualTo("owner@example.com");
        assertThat(event.getValue().phone()).isEqualTo("+380671234567");
        assertThat(event.getValue().socialMedias()).as("ALL means email and SMS, no messenger").isEmpty();
        assertThat(event.getValue().deduplicationKey()).isEqualTo("match:1:2");
        assertThat(event.getValue().subject()).isEqualTo("Вашу річ знайшли");
        assertThat(event.getValue().message())
                .contains("Загублений рюкзак", "Знайдений рюкзак", "Метро Хрещатик", "finder@example.com")
                .doesNotContain("{0}", "notification.match.body");
        assertThat(event.getValue().smsMessage())
                .isEqualTo("Схоже, знайшли «Загублений рюкз...»: +380 50 987 6543")
                .hasSizeLessThanOrEqualTo(70);

        assertThat(match.getNotifiedAt()).isNotNull();
        assertThat(match.getNotifiedBy()).isEqualTo("admin@derechi.local");
        assertThat(notified.lost().id()).isEqualTo(1L);
        assertThat(notified.candidate().found().id()).isEqualTo(2L);
        assertThat(notified.candidate().notifiedAt()).isEqualTo(match.getNotifiedAt());
        assertThat(notified.candidate().notifiedBy()).isEqualTo("admin@derechi.local");
        verify(repository).save(match);
    }

    @Test
    void leavesTheEmailOutOfTheMessageWhenTheFinderHasNone() {
        SimilarItem match = match();
        match.getFoundItem().getInfo().setEmail(null);
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL);

        var event = forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("notification.exchange"), eq("notification.match.found"), event.capture());
        assertThat(event.getValue().message())
                .contains("Знайдений рюкзак")
                .doesNotContain("null", " та ", "{6}");
    }

    @Test
    void fitsTheSmsIntoOnePaidPartInEveryLanguage() {
        SimilarItem match = match();
        match.getLostItem().setTitle("Чорний шкіряний рюкзак з ноутбуком");
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        for (String language : new String[]{"en", "uk", "pl", "de", "fr"}) {
            LocaleContextHolder.setLocale(Locale.of(language));
            service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL);
        }

        var events = forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate, times(5))
                .convertAndSend(eq("notification.exchange"), eq("notification.match.found"), events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> assertThat(event.smsMessage())
                .as(event.smsMessage())
                .startsWith("DeRechi")
                .contains("Чорний шкіряний...", "+380 50 987 6543")
                .doesNotContain("{", "finder@example.com", "Метро Хрещатик")
                .hasSizeLessThanOrEqualTo(70));
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Загублений рюкзак        | Загублений рюкз...",
            "Ключі від кварт          | Ключі від кварт",
            "Синій гаманець і картки  | Синій гаманець..."})
    void cutsTitlesLongerThanFifteenCharactersForTheSms(String title, String expected) {
        assertThat(MatchNotificationService.shortTitle(title)).isEqualTo(expected);
    }

    @Test
    void refusesAnOwnerWhoLeftOnlyANickNameAndSendsNothing() {
        SimilarItem match = match();
        ContactInfo owner = match.getLostItem().getInfo();
        owner.setPhone(null);
        owner.setNickName("@lostcat");
        owner.setProfileUrl("https://t.me/lostcat");
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.EMAIL))
                .isInstanceOf(OwnerWithoutPhoneException.class)
                .extracting("lostItemId").isEqualTo(1L);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
        verify(repository, never()).save(any());
        assertThat(match.getNotifiedAt()).isNull();
    }

    @Test
    void givesTheOwnerTheFinderNickNameWhenTheFinderLeftNoPhone() {
        SimilarItem match = match();
        ContactInfo finder = match.getFoundItem().getInfo();
        finder.setPhone(null);
        finder.setEmail(null);
        finder.setNickName("@finder");
        finder.setProfileUrl("https://t.me/finder");
        when(repository.findById(new SimilarItem.SimilarItemId(2L, 1L))).thenReturn(Optional.of(match));

        service.notifyOwner(1L, 2L, "admin@derechi.local", NotifyChannel.ALL);

        var event = forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("notification.exchange"), eq("notification.match.found"), event.capture());
        assertThat(event.getValue().message())
                .contains("@finder (https://t.me/finder)")
                .doesNotContain("null", " - ");
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
