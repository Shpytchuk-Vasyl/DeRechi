package org.shpytchuk.ingest.source.facebook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.FIRST_SCRIPT;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.SECOND_SCRIPT;

class FacebookGroupReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI GROUP_URL = URI.create("https://www.facebook.com/groups/zzter/?sorting_setting=CHRONOLOGICAL");

    private final FacebookClient client = mock(FacebookClient.class);
    private final AtomicInteger pauses = new AtomicInteger();
    private final FacebookGroupReader reader = new FacebookGroupReader(client, JSON, 5, pauses::incrementAndGet);

    @BeforeEach
    void facebook() {
        when(client.page(GROUP_URL)).thenReturn(FacebookFixtures.page());
        when(client.script(FIRST_SCRIPT)).thenReturn("var nothing;");
        when(client.script(SECOND_SCRIPT)).thenReturn(FacebookFixtures.feedQueryScript());
        when(client.graphQl(anyString(), anyMap(), anyString(), any()))
                .thenReturn(FacebookFixtures.feed(), FacebookFixtures.lastPage());
    }

    @Test
    void readsPagesUntilTheStoriesAreOlderThanSince() {
        FacebookGroupReader.GroupFeed feed = reader.read("zzter", FacebookFixtures.THIRD.plusSeconds(60));

        assertThat(feed.name()).isEqualTo("ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ");
        assertThat(feed.stories()).extracting(FacebookStory::postId).containsExactly("9003", "9002", "9001");
        assertThat(pauses).hasValue(1);
    }

    @Test
    void stopsAtTheLastPage() {
        FacebookGroupReader.GroupFeed feed = reader.read("zzter", Instant.EPOCH);

        assertThat(feed.stories()).extracting(FacebookStory::postId).containsExactly("9003", "9002", "9001", "8001");
        verify(client, times(2)).graphQl(anyString(), anyMap(), anyString(), any());
    }

    @Test
    void readsAtMostMaxPages() {
        when(client.graphQl(anyString(), anyMap(), anyString(), any())).thenReturn(FacebookFixtures.feed());

        new FacebookGroupReader(client, JSON, 3, () -> {
        }).read("zzter", Instant.EPOCH);

        verify(client, times(2)).graphQl(anyString(), anyMap(), anyString(), any());
    }

    @Test
    void sendsTheSessionTokensAndTheCursorWithTheFeedQuery() {
        reader.read("zzter", FacebookFixtures.THIRD.plusSeconds(60));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(client).graphQl(eq(FacebookGroupReader.FEED_QUERY), form.capture(), eq("lsd-token"), eq(GROUP_URL));
        assertThat(form.getValue())
                .containsEntry("av", "100000000000001")
                .containsEntry("__user", "100000000000001")
                .containsEntry("fb_dtsg", "dtsg-token")
                .containsEntry("jazoest", FacebookGroupReader.jazoest("dtsg-token"))
                .containsEntry("lsd", "lsd-token")
                .containsEntry("doc_id", FacebookFixtures.DOC_ID);
        JsonNode variables = JSON.readTree(form.getValue().get("variables"));
        assertThat(variables.path("cursor").asString()).isEqualTo("cursor-1");
        assertThat(variables.path("id").asString()).isEqualTo("153439147746716");
        assertThat(variables.path("sortingSetting").asString()).isEqualTo("CHRONOLOGICAL");
        assertThat(variables.path("__relay_internal__pv__GroupsCometGYSJFeedItemHeightrelayprovider").asInt())
                .isEqualTo(150);
    }

    @Test
    void looksUpTheDocIdOnlyOnce() {
        reader.read("zzter", Instant.EPOCH);
        reader.read("zzter", Instant.EPOCH);

        verify(client, times(1)).script(SECOND_SCRIPT);
    }

    @Test
    void looksUpTheDocIdAgainWhenTheFeedQueryFails() {
        reader.read("zzter", FacebookFixtures.THIRD.plusSeconds(60));
        when(client.graphQl(anyString(), anyMap(), anyString(), any()))
                .thenThrow(new IngestException("Facebook feed answered without stories: PersistedQueryNotFound"))
                .thenReturn(FacebookFixtures.feed());

        FacebookGroupReader.GroupFeed feed = reader.read("zzter", FacebookFixtures.THIRD.plusSeconds(60));

        assertThat(feed.stories()).hasSize(3);
        verify(client, times(2)).script(SECOND_SCRIPT);
    }

    @Test
    void aLoggedOutPageMakesTheSourceUnavailable() {
        when(client.page(GROUP_URL)).thenReturn(FacebookFixtures.anonymousPage());

        assertThatThrownBy(() -> reader.read("zzter", Instant.EPOCH))
                .isInstanceOf(StageUnavailableException.class)
                .hasMessageContaining("FACEBOOK_COOKIES");
        verify(client, never()).graphQl(anyString(), anyMap(), anyString(), any());
    }

    @Test
    void failsWhenNoScriptHasTheDocId() {
        when(client.script(SECOND_SCRIPT)).thenReturn("var nothing;");

        assertThatThrownBy(() -> reader.read("zzter", Instant.EPOCH))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("doc_id not found in 2 scripts");
    }
}
