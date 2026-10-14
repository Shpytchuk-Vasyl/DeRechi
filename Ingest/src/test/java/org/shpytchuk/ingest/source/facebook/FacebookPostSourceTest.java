package org.shpytchuk.ingest.source.facebook;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.config.facebook.FacebookProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.SECOND_SCRIPT;

class FacebookPostSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T14:30:00Z");
    private static final URI ZZTER = URI.create("https://www.facebook.com/groups/zzter/?sorting_setting=CHRONOLOGICAL");
    private static final URI LVIV = URI.create("https://www.facebook.com/groups/lviv_lost/?sorting_setting=CHRONOLOGICAL");

    private final FacebookClient client = mock(FacebookClient.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<IncomingPost> received = new ArrayList<>();

    @BeforeEach
    void facebook() {
        when(client.page(any())).thenReturn(FacebookFixtures.page());
        when(client.script(any())).thenReturn("var nothing;");
        when(client.script(SECOND_SCRIPT)).thenReturn(FacebookFixtures.feedQueryScript());
        when(client.graphQl(anyString(), anyMap(), anyString(), any()))
                .thenReturn(FacebookFixtures.feed(), FacebookFixtures.lastPage());
    }

    @Test
    void withoutCookiesItReadsNothing() {
        source(properties("", "zzter=Тернопіль")).start(received::add);

        verifyNoInteractions(scheduler, client);
    }

    @Test
    void theFirstPollComesAMinuteAfterTheStart() {
        source(properties("c_user=1; xs=2", "zzter=Тернопіль")).start(received::add);

        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plus(Duration.ofMinutes(1))));
    }

    @Test
    void sendsThePostsOfTheCatchUpWindowOldestFirst() {
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль"));

        source.poll();

        assertThat(received).extracting(post -> post.key().id()).containsExactly("9001", "9002", "9003");
        IncomingPost newest = received.getLast();
        assertThat(newest.key()).isEqualTo(new PostKey("facebook", "zzter", "9003"));
        assertThat(newest.sourceTitle()).isEqualTo("ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ");
        assertThat(newest.link()).isEqualTo("https://www.facebook.com/groups/zzter/permalink/9003/");
        assertThat(newest.postedAt()).isEqualTo(FacebookFixtures.NEWEST);
        assertThat(newest.text()).isEqualTo("Знайдено правий навушник біля 6 школи");
        assertThat(newest.photos()).extracting(PhotoRef::id).containsExactly(
                "https://scontent.example/9003-a.jpg", "https://scontent.example/9003-b.jpg");
        assertThat(newest.settings()).isEqualTo(new SourceSettings("UA", "Тернопіль", "uk"));
    }

    @Test
    void theNextPollComesAfterAnHourPlusARandomPartOfTheJitter() {
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль"));

        source.poll();

        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(2)).schedule(any(Runnable.class), next.capture());
        assertThat(next.getValue()).isBetween(NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofMinutes(80)));
    }

    @Test
    void groupsAreReadInsideTheirLocalActiveHours() {
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль", "07:00-22:00"));

        source.poll();

        assertThat(received).isNotEmpty();
    }

    @Test
    void outsideTheActiveHoursNothingIsRequestedAndTheNextPollWaitsForTheMorning() {
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль", "07:00-17:00"));

        source.poll();

        verifyNoInteractions(client);
        ArgumentCaptor<Instant> next = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler, times(2)).schedule(any(Runnable.class), next.capture());
        Instant kyivSevenTomorrow = Instant.parse("2026-10-14T04:00:00Z");
        assertThat(next.getValue()).isBetween(kyivSevenTomorrow, kyivSevenTomorrow.plus(Duration.ofMinutes(20)));
    }

    @Test
    void theNextPollTakesOnlyWhatIsNewSinceTheLastOne() {
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль"));
        source.poll();
        received.clear();
        when(client.graphQl(anyString(), anyMap(), anyString(), any())).thenReturn(FacebookFixtures.feed());

        source.poll();

        assertThat(received).extracting(post -> post.key().id()).containsExactly("9003");
    }

    @Test
    void aLoggedOutSessionIsReportedAndTheOtherGroupsAreStillTried() {
        when(client.page(ZZTER)).thenReturn(FacebookFixtures.anonymousPage());
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль;lviv_lost=Львів"));

        source.poll();

        assertThat(registry.get("derechi.ingest.facebook.authorized").gauge().value()).isEqualTo(1);
        assertThat(registry.get("derechi.ingest.facebook.polls").tag("group", "zzter").tag("outcome", "unauthorized")
                .counter().count()).isEqualTo(1);
        assertThat(received).isNotEmpty().allSatisfy(post -> assertThat(post.key().channel()).isEqualTo("lviv_lost"));
    }

    @Test
    void theGaugeDropsWhileEveryGroupIsLoggedOut() {
        when(client.page(any())).thenReturn(FacebookFixtures.anonymousPage());
        FacebookPostSource source = started(properties("c_user=1; xs=2", "zzter=Тернопіль"));

        source.poll();

        assertThat(registry.get("derechi.ingest.facebook.authorized").gauge().value()).isZero();
        assertThat(received).isEmpty();
    }

    @Test
    void aFailedGroupIsReadFromTheSamePointNextTime() {
        when(client.page(LVIV)).thenThrow(new IngestException("Facebook page failed: HTTP 500"));
        FacebookPostSource source = started(properties("c_user=1; xs=2", "lviv_lost=Львів"));

        source.poll();

        assertThat(received).isEmpty();
        assertThat(registry.get("derechi.ingest.facebook.polls").tag("outcome", "failed").counter().count()).isEqualTo(1);

        doReturn(FacebookFixtures.page()).when(client).page(LVIV);
        source.poll();

        assertThat(received).extracting(post -> post.key().id()).containsExactly("9001", "9002", "9003");
    }

    @Test
    void downloadsAPhotoByItsUrl() {
        when(client.download(URI.create("https://scontent.example/9001.jpg"))).thenReturn(new byte[]{1, 2});

        byte[] photo = source(properties("c_user=1", "zzter=")).download(
                new PhotoRef("https://scontent.example/9001.jpg", 960, 720));

        assertThat(photo).containsExactly(1, 2);
    }

    private FacebookPostSource started(FacebookProperties properties) {
        FacebookPostSource source = source(properties);
        source.start(received::add);
        return source;
    }

    private FacebookPostSource source(FacebookProperties properties) {
        return new FacebookPostSource(properties, client, JsonMapper.builder().build(), scheduler,
                Clock.fixed(NOW, ZoneOffset.UTC), registry);
    }

    private static FacebookProperties properties(String cookies, String groups) {
        return properties(cookies, groups, "");
    }

    private static FacebookProperties properties(String cookies, String groups, String activeHours) {
        return new FacebookProperties(cookies, groups, null, Duration.ofHours(1), Duration.ofMinutes(20), Duration.ZERO,
                5, Duration.ofHours(48), Duration.ofSeconds(5), activeHours);
    }
}
