package org.shpytchuk.ingest.telegram;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.SourceSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorPhoneResolverTest {

    private static final SourceSettings UA = new SourceSettings("UA", "Львів", "uk");

    private final FakeTelegram telegram = new FakeTelegram();
    private final AuthorPhoneResolver resolver = new AuthorPhoneResolver(telegram, new PhoneExtractor());

    @Test
    void authorWithAVisibleNumberGetsItInE164() {
        telegram.phones.put(777L, "380671234567");

        assertThat(resolver.resolve(post(777L, List.of(), List.of()))).contains("+380671234567");
    }

    @Test
    void authorWithAHiddenNumberGivesNothing() {
        telegram.phones.put(777L, "");

        assertThat(resolver.resolve(post(777L, List.of(), List.of()))).isEmpty();
    }

    @Test
    void channelPostWithoutForwardAndWithoutMentionsGivesNothing() {
        assertThat(resolver.resolve(post(null, List.of(), List.of()))).isEmpty();
        assertThat(telegram.phoneLookups).isEmpty();
    }

    @Test
    void contactMentionedByUsernameIsResolvedThroughTheProfile() {
        telegram.users.put("finder_example", 4242L);
        telegram.phones.put(4242L, "380501112233");

        Optional<String> phone = resolver.resolve(post(null, List.of("Finder_Example"), List.of()));

        assertThat(phone).contains("+380501112233");
        assertThat(telegram.usernameLookups).containsExactly("finder_example");
    }

    @Test
    void authorGoesBeforeLinkedUsersAndLinkedUsersBeforeUsernames() {
        telegram.phones.put(777L, "");
        telegram.phones.put(101L, "380671234567");
        telegram.users.put("someone", 202L);
        telegram.phones.put(202L, "380501112233");

        assertThat(resolver.resolve(post(777L, List.of("someone"), List.of(101L)))).contains("+380671234567");
        assertThat(telegram.phoneLookups).containsExactly(777L, 101L);
        assertThat(telegram.usernameLookups).isEmpty();
    }

    @Test
    void theSameUserIsAskedOnlyOnce() {
        telegram.users.put("author", 777L);

        resolver.resolve(post(777L, List.of("author"), List.of(777L)));

        assertThat(telegram.phoneLookups).containsExactly(777L);
    }

    @Test
    void usernameOfAChannelOrABotIsSkipped() {
        telegram.users.put("lost_channel", null);
        telegram.users.put("person", 303L);
        telegram.phones.put(303L, "380671234567");

        assertThat(resolver.resolve(post(null, List.of("lost_channel", "person"), List.of()))).contains("+380671234567");
    }

    @Test
    void theChatsOwnUsernameIsNotLookedUp() {
        resolver.resolve(post(null, List.of("ZAGUBLENO911"), List.of()));

        assertThat(telegram.usernameLookups).isEmpty();
    }

    @Test
    void atMostThreeUsernamesAreLookedUpPerPost() {
        resolver.resolve(post(null, List.of("a1", "a2", "a3", "a4", "a5"), List.of()));

        assertThat(telegram.usernameLookups).containsExactly("a1", "a2", "a3");
    }

    @Test
    void cachedUsernamesDoNotCountAgainstTheLimit() {
        resolver.resolve(post(null, List.of("a1", "a2", "a3"), List.of()));
        telegram.usernameLookups.clear();
        telegram.users.put("a4", 404L);
        telegram.phones.put(404L, "380671234567");

        Optional<String> phone = resolver.resolve(post(null, List.of("a1", "a2", "a3", "a4"), List.of()));

        assertThat(phone).contains("+380671234567");
        assertThat(telegram.usernameLookups).containsExactly("a4");
    }

    @Test
    void usernameAnswersAreCachedIgnoringCaseIncludingNotAUser() {
        telegram.users.put("person", 303L);

        resolver.resolve(post(null, List.of("person", "nobody"), List.of()));
        resolver.resolve(post(null, List.of("Person", "NOBODY"), List.of()));

        assertThat(telegram.usernameLookups).containsExactly("person", "nobody");
    }

    @Test
    void failedUsernameLookupIsNotCachedAndDoesNotStopTheOthers() {
        telegram.failingUsernames.add("flaky");
        telegram.users.put("person", 303L);
        telegram.phones.put(303L, "380671234567");

        assertThat(resolver.resolve(post(null, List.of("flaky", "person"), List.of()))).contains("+380671234567");

        telegram.failingUsernames.clear();
        telegram.usernameLookups.clear();
        resolver.resolve(post(null, List.of("flaky"), List.of()));
        assertThat(telegram.usernameLookups).containsExactly("flaky");
    }

    @Test
    void failedPhoneLookupCountsAsNoPhone() {
        telegram.failingUsers.add(777L);
        telegram.phones.put(101L, "380671234567");

        assertThat(resolver.resolve(post(777L, List.of(), List.of(101L)))).contains("+380671234567");
    }

    @Test
    void sessionThatIsNotReadyFailsTheStage() {
        telegram.ready = false;

        assertThatThrownBy(() -> resolver.resolve(post(777L, List.of(), List.of())))
                .isInstanceOf(StageUnavailableException.class);
    }

    @Test
    void invalidProfileNumberIsSkipped() {
        telegram.phones.put(777L, "12345");
        telegram.phones.put(101L, "380671234567");

        assertThat(resolver.resolve(post(777L, List.of(), List.of(101L)))).contains("+380671234567");
    }

    @Test
    void foreignProfileNumberKeepsItsCountryCode() {
        telegram.phones.put(777L, "48512345678");

        assertThat(resolver.resolve(post(777L, List.of(), List.of()))).contains("+48512345678");
    }

    private static IncomingPost post(Long author, List<String> usernames, List<Long> userIds) {
        return new IncomingPost(new PostKey("telegram", "-100", Long.toString(1L << 20)), "Загублено", null,
                Instant.parse("2026-10-10T09:00:00Z"), "Загублено паспорт", List.of(), UA,
                new TelegramDetails(-100L, 1L << 20, "zagubleno911", author, usernames, userIds));
    }

    private static final class FakeTelegram implements TelegramSource {

        final Map<Long, String> phones = new HashMap<>();
        final Map<String, Long> users = new HashMap<>();
        final Set<String> failingUsernames = new HashSet<>();
        final Set<Long> failingUsers = new HashSet<>();
        final List<Long> phoneLookups = new ArrayList<>();
        final List<String> usernameLookups = new ArrayList<>();
        boolean ready = true;

        @Override
        public void start(Consumer<IncomingPost> sink) {
        }

        @Override
        public List<IncomingPost> recentPosts(Duration maxAge, int maxMessages) {
            return List.of();
        }

        @Override
        public Optional<String> userPhone(long userId) {
            if (!ready) {
                throw new StageUnavailableException("Telegram session is not authorized");
            }
            phoneLookups.add(userId);
            if (failingUsers.contains(userId)) {
                throw new IngestException("Telegram GetUser timed out after 30 s");
            }
            String phone = phones.get(userId);
            return phone == null || phone.isEmpty() ? Optional.empty() : Optional.of(phone);
        }

        @Override
        public Optional<Long> userIdByUsername(String username) {
            usernameLookups.add(username);
            if (failingUsernames.contains(username)) {
                throw new IngestException("Telegram SearchPublicChat failed: 429 Too Many Requests: retry after 30");
            }
            return Optional.ofNullable(users.get(username));
        }

        @Override
        public byte[] download(PhotoRef photo) {
            return new byte[0];
        }

        @Override
        public boolean authorized() {
            return ready;
        }
    }
}
