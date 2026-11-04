package org.shpytchuk.ingest.source.facebook;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.source.PhotoRef;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.shpytchuk.ingest.source.facebook.ApifyFixtures.photo;

class ApifyGroupReaderTest {

    private final ApifyClient client = mock(ApifyClient.class);
    private final ApifyGroupReader reader = new ApifyGroupReader(client);

    @Test
    void turnsThePostsIntoStoriesOfTheGroup() {
        answer(ApifyFixtures.posts());

        GroupFeed feed = reader.read("zzter", Instant.EPOCH);

        assertThat(feed.name()).isEqualTo(ApifyFixtures.GROUP_TITLE);
        assertThat(feed.stories()).extracting(FacebookStory::postId).containsExactly("9001", "9002");
        FacebookStory first = feed.stories().getFirst();
        assertThat(first.createdAt()).isEqualTo(ApifyFixtures.FIRST);
        assertThat(first.text()).isEqualTo("Загубила ключі біля ринку");
        assertThat(first.photos()).containsExactly(new PhotoRef("https://scontent.example/9001.jpg", 960, 720));
        assertThat(first.actor()).isEqualTo(new FacebookActor("Олена Коваль",
                "https://www.facebook.com/profile.php?id=100012345678901"));
        assertThat(feed.stories().get(1).photos()).isEmpty();
    }

    @Test
    void videosAndRepeatedPhotosAreLeftOut() {
        ApifyPost.Attachment video = new ApifyPost.Attachment("Video",
                new ApifyPost.Image("https://scontent.example/video.jpg", 640, 360));
        answer(List.of(post("9001", List.of(photo("https://scontent.example/a.jpg"), video,
                photo("https://scontent.example/a.jpg")))));

        assertThat(reader.read("zzter", Instant.EPOCH).stories().getFirst().photos())
                .extracting(PhotoRef::id).containsExactly("https://scontent.example/a.jpg");
    }

    @Test
    void anAuthorWithoutANumericIdInTheStoryIdIsUnknown() {
        answer(List.of(new ApifyPost("not-base64", "9001", ApifyFixtures.FIRST, "Загублено", null,
                new ApifyPost.User("Олена"), null, null)));

        GroupFeed feed = reader.read("zzter", Instant.EPOCH);

        assertThat(feed.stories().getFirst().actor()).isNull();
        assertThat(feed.name()).isEqualTo("zzter");
    }

    @Test
    void postsWithoutAnIdOrATimeAreSkipped() {
        answer(List.of(post("9001", null),
                new ApifyPost(null, null, ApifyFixtures.FIRST, "без id", null, null, null, null),
                new ApifyPost(null, "9003", null, "без часу", null, null, null, null)));

        assertThat(reader.read("zzter", Instant.EPOCH).stories()).extracting(FacebookStory::postId)
                .containsExactly("9001");
    }

    @Test
    void anErrorInsteadOfPostsFailsTheGroup() {
        answer(List.of(new ApifyPost(null, null, null, null, null, null, null, "Group is private")));

        assertThatThrownBy(() -> reader.read("closed", Instant.EPOCH))
                .isInstanceOf(IngestException.class)
                .hasMessageContaining("Group is private");
    }

    private void answer(List<ApifyPost> posts) {
        when(client.groupPosts(anyString(), any())).thenReturn(posts);
    }

    private static ApifyPost post(String legacyId, List<ApifyPost.Attachment> attachments) {
        return new ApifyPost(null, legacyId, ApifyFixtures.FIRST, "Загублено", null, null, attachments, null);
    }
}
