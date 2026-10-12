package org.shpytchuk.ingest.pipeline;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.entity.IngestPost;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.repository.IngestPostRepository;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.support.AbstractPostgresTests;
import org.shpytchuk.ingest.support.Posts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostLedger.class, PostLedgerTests.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostLedgerTests extends AbstractPostgresTests {

    static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Autowired
    PostLedger ledger;

    @Autowired
    IngestPostRepository repository;

    @Test
    void recordsAPostOnceAndRemembersIt() {
        IncomingPost post = post(10);

        assertThat(ledger.seen(post)).isFalse();
        ledger.record(post, PostStatus.SKIPPED, "hash", null);

        assertThat(ledger.seen(post)).isTrue();
        IngestPost row = repository.findBySourceAndChannelAndExternalId("telegram", "-100", "10").orElseThrow();
        assertThat(row.getStatus()).isEqualTo(PostStatus.SKIPPED);
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void onlyNoticesMakeLaterPostsDuplicates() {
        ledger.record(post(1), PostStatus.SKIPPED, "skipped-hash", null);
        ledger.record(post(2), PostStatus.PREVIEWED, "notice-hash", ItemKind.LOST);

        assertThat(ledger.duplicate(List.of("skipped-hash"), Duration.ofDays(14))).isFalse();
        assertThat(ledger.duplicate(List.of("other", "notice-hash"), Duration.ofDays(14))).isTrue();
    }

    @Test
    void duplicatesExpireWithTheWindow() {
        ledger.record(post(1), PostStatus.PUBLISHED, "notice-hash", ItemKind.FOUND);

        assertThat(ledger.duplicate(List.of("notice-hash"), Duration.ZERO)).isFalse();
    }

    @Test
    void publishedKeepsTheItemId() {
        long rowId = ledger.record(post(3), PostStatus.PUBLISHING, "h", ItemKind.FOUND);

        ledger.published(rowId, 42);

        IngestPost row = repository.findById(rowId).orElseThrow();
        assertThat(row.getItemId()).isEqualTo(42);
        assertThat(row.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void parksPostsInterruptedWhilePublishing() {
        long interrupted = ledger.record(post(4), PostStatus.PUBLISHING, "h", ItemKind.LOST);
        long done = ledger.record(post(5), PostStatus.PUBLISHED, "h2", ItemKind.LOST);

        assertThat(ledger.parkInterrupted()).isEqualTo(1);

        assertThat(repository.findById(interrupted).orElseThrow().getStatus()).isEqualTo(PostStatus.NEEDS_CHECK);
        assertThat(repository.findById(done).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    static IncomingPost post(long messageId) {
        return Posts.telegram(messageId, "text", List.of(), NOW);
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
