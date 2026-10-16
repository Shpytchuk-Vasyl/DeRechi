package org.shpytchuk.ingest.source.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;
import org.shpytchuk.ingest.support.Posts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorProfileResolverTest {

    private static final SourceSettings UA = new SourceSettings("UA", "Львів", "uk");

    private final TelegramSource telegram = mock(TelegramSource.class);
    private final AuthorProfileResolver resolver = new AuthorProfileResolver(telegram);

    @Test
    void theAuthorsUsernameIsTheProfile() {
        when(telegram.username(777L)).thenReturn(Optional.of("Finder_Lviv"));

        assertThat(resolver.resolve(post(777L)))
                .contains(new AuthorProfile(SocialMedia.TELEGRAM, "@Finder_Lviv", "https://t.me/Finder_Lviv"));
    }

    @Test
    void anAuthorWithoutAUsernameHasNoProfile() {
        when(telegram.username(777L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(post(777L))).isEmpty();
    }

    @Test
    void aChannelPostWithoutAnAuthorHasNoProfile() {
        assertThat(resolver.resolve(post(null))).isEmpty();
        verifyNoInteractions(telegram);
    }

    @Test
    void aFailedLookupCountsAsNoProfile() {
        when(telegram.username(anyLong())).thenThrow(new IngestException("Telegram GetUser timed out after 30 s"));

        assertThat(resolver.resolve(post(777L))).isEmpty();
    }

    @Test
    void aSessionThatIsNotReadyFailsTheStage() {
        when(telegram.username(anyLong())).thenThrow(new StageUnavailableException("Telegram session is not authorized"));

        assertThatThrownBy(() -> resolver.resolve(post(777L))).isInstanceOf(StageUnavailableException.class);
    }

    @Test
    void aPostOfAnotherSourceHasNoTelegramProfile() {
        IncomingPost facebook = Posts.facebook("9001", "Загублено гаманець", null, Instant.parse("2026-10-10T09:00:00Z"));

        assertThat(resolver.resolve(facebook)).isEmpty();
        verifyNoInteractions(telegram);
    }

    private static IncomingPost post(Long author) {
        return new IncomingPost(new PostKey("telegram", "-100", Long.toString(1L << 20)), "Загублено", null,
                Instant.parse("2026-10-10T09:00:00Z"), "Загублено паспорт, пишіть", List.of(), UA,
                new TelegramDetails(-100L, 1L << 20, "zagubleno911", author, List.of(), List.of()));
    }
}
