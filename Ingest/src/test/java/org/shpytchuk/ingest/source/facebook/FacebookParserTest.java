package org.shpytchuk.ingest.source.facebook;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.PhotoRef;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.FIRST_SCRIPT;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.NEWEST;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.SECOND;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.SECOND_SCRIPT;
import static org.shpytchuk.ingest.source.facebook.FacebookFixtures.THIRD;

class FacebookParserTest {

    private final FacebookParser parser = new FacebookParser(JsonMapper.builder().build());

    @Test
    void readsTheSessionTokensAndTheFeedQueryOfTheGroupPage() {
        GroupPage page = parser.groupPage(FacebookFixtures.page());

        assertThat(page.userId()).isEqualTo("100000000000001");
        assertThat(page.fbDtsg()).isEqualTo("dtsg-token");
        assertThat(page.lsd()).isEqualTo("lsd-token");
        assertThat(page.name()).isEqualTo("ЗАГУБИВ-ЗНАЙШОВ ТЕРНОПІЛЬ");
        assertThat(page.groupId()).isEqualTo("153439147746716");
        assertThat(page.feedVariables().has("__relay_internal__pv__GroupsCometGYSJFeedItemHeightrelayprovider")).isTrue();
        assertThat(page.scripts()).containsExactly(FIRST_SCRIPT, SECOND_SCRIPT);
    }

    @Test
    void takesTheFirstStoryOnceWithItsLargePhotosAndTheFeedCursor() {
        FeedPage feed = parser.groupPage(FacebookFixtures.page()).feed();

        assertThat(feed.stories()).singleElement().satisfies(story -> {
            assertThat(story.postId()).isEqualTo("9003");
            assertThat(story.createdAt()).isEqualTo(NEWEST);
            assertThat(story.text()).isEqualTo("Знайдено правий навушник біля 6 школи");
            assertThat(story.photos()).containsExactly(
                    new PhotoRef("https://scontent.example/9003-a.jpg", 1536, 2048),
                    new PhotoRef("https://scontent.example/9003-b.jpg", 1536, 2048));
        });
        assertThat(feed.nextCursor()).isEqualTo("cursor-1");
    }

    @Test
    void anAnonymousPageMakesTheSourceUnavailable() {
        assertThatThrownBy(() -> parser.groupPage(FacebookFixtures.anonymousPage()))
                .isInstanceOf(StageUnavailableException.class)
                .hasMessageContaining("FACEBOOK_COOKIES");
    }

    @Test
    void aLoginPageWithoutTheFeedMakesTheSourceUnavailable() {
        String login = "<title>Log in to Facebook</title><script>[\"LSD\",[],{\"token\":\"lsd-token\"}],"
                + "{\"USER_ID\":\"0\"}</script>";

        assertThatThrownBy(() -> parser.groupPage(login))
                .isInstanceOf(StageUnavailableException.class)
                .hasMessageContaining("FACEBOOK_COOKIES");
    }

    @Test
    void aLoggedInPageWithoutTheFeedFailsWithItsTitle() {
        String checkpoint = "<title>Security check</title><script>[\"LSD\",[],{\"token\":\"lsd-token\"}],"
                + "{\"USER_ID\":\"100000000000001\"}</script>";

        assertThatThrownBy(() -> parser.groupPage(checkpoint))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("\"Security check\" has no feed query");
    }

    @Test
    void aCheckpointedAccountMakesTheSourceUnavailable() {
        assertThatThrownBy(() -> parser.groupPage(FacebookFixtures.checkpointPage()))
                .isInstanceOf(FacebookActionRequiredException.class)
                .hasMessageContaining("checkpoint");
    }

    @Test
    void aCheckpointedPageWithoutTheLsdTokenIsACheckpointToo() {
        String checkpoint = FacebookFixtures.checkpointPage().replace("LSD", "NO_LSD");

        assertThatThrownBy(() -> parser.groupPage(checkpoint)).isInstanceOf(FacebookActionRequiredException.class);
    }

    @Test
    void anAccountThatIsNotCheckpointedFailsWithTheTitleOfThePage() {
        String page = FacebookFixtures.checkpointPage().replace("\"is_checkpointed\":true", "\"is_checkpointed\":false");

        assertThatThrownBy(() -> parser.groupPage(page))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("\"Facebook\" has no feed query");
    }

    @Test
    void aPageWithoutTheLsdTokenFails() {
        assertThatThrownBy(() -> parser.groupPage("<title>Facebook</title>\"USER_ID\":\"100000000000001\""))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("LSD");
    }

    @Test
    void readsTheStreamedFeedPageDespiteFieldErrors() {
        FeedPage feed = parser.feedPage(FacebookFixtures.feed());

        assertThat(feed.stories()).extracting(FacebookStory::postId).containsExactly("9002", "9001");
        assertThat(feed.stories()).extracting(FacebookStory::createdAt).containsExactly(SECOND, THIRD);
        assertThat(feed.stories().getFirst().photos()).isEmpty();
        assertThat(feed.stories().getLast().photos())
                .containsExactly(new PhotoRef("https://scontent.example/9001.jpg", 960, 720));
        assertThat(feed.nextCursor()).isEqualTo("cursor-2");
    }

    @Test
    void theLastPageHasNoCursor() {
        FeedPage feed = parser.feedPage(FacebookFixtures.lastPage());

        assertThat(feed.stories()).extracting(FacebookStory::postId).containsExactly("8001");
        assertThat(feed.nextCursor()).isNull();
    }

    @Test
    void anErrorAnswerFails() {
        assertThatThrownBy(() -> parser.feedPage("for (;;);{\"error\":1357001,\"errorSummary\":\"Not logged in\"}"))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("Not logged in");
    }

    @Test
    void anAnswerWithErrorsOnlyFails() {
        assertThatThrownBy(() -> parser.feedPage("{\"errors\":[{\"message\":\"PersistedQueryNotFound\"}]}"))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("PersistedQueryNotFound");
    }

    @Test
    void findsTheDocIdOfAQueryInAScript() {
        assertThat(FacebookParser.docId(FacebookFixtures.feedQueryScript(), FacebookGroupReader.FEED_QUERY))
                .contains(FacebookFixtures.DOC_ID);
        assertThat(FacebookParser.docId("var a = 1;", FacebookGroupReader.FEED_QUERY)).isEmpty();
    }
}
