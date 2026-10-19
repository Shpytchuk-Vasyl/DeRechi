package org.shpytchuk.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.shpytchuk.ingest.entity.IngestPost;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.extraction.Extraction;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.extraction.Extractor;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.place.PlaceResolver;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.repository.IngestPostRepository;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostKey;
import org.shpytchuk.ingest.source.PostSource;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "derechi.ingest.publishers=site",
        "derechi.telegram.api-id=0"
})
@EnabledIfEnvironmentVariable(named = "CLIENT_API_URL", matches = ".+")
@Import(SiteFlowIT.StackSourceConfig.class)
class SiteFlowIT {

    static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");
    static final Duration EVENTUALLY = Duration.ofSeconds(30);

    @Autowired
    StackSource source;

    @Autowired
    IngestPostRepository posts;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Extractor extractor;

    @MockitoBean
    PlaceResolver places;

    @DynamicPropertySource
    static void stack(DynamicPropertyRegistry registry) {
        registry.add("derechi.api.uri", () -> System.getenv("CLIENT_API_URL"));
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("POSTGRES_PASSWORD"));
    }

    @Test
    void lostNoticeFromAPostIsPublishedOnTheSite() {
        Phone phone = Phone.random();
        String title = "Гаманець " + shortId();
        String placeId = "ingest-it-" + shortId();
        resolvesTo(new ResolvedPlace(placeId, "площа Ринок", 49.8419, 24.0315, "UA"));
        extracts(lost(title, new Money(500, null)));

        IncomingPost post = source.emit("Загубив чорний гаманець біля Ратуші, " + phone.national() + ", є Viber");

        IngestPost row = settled(post);
        assertThat(row.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(row.getItemKind()).isEqualTo(ItemKind.LOST);
        assertThat(onTheSite(row.getItemId())).isEqualTo(new SiteItem(title, today(), 500, "UAH", "WALLET",
                "площа Ринок", "UA", phone.e164(), null, 1));
    }

    @Test
    void noticeRejectedByClientApiFailsInsteadOfWaitingForACheck() {
        Phone phone = Phone.random();
        resolvesTo(new ResolvedPlace("ingest-it-" + shortId(), "площа Ринок", 49.8419, 24.0315, "UA"));
        extracts(lost("Ключі " + shortId(), new Money(500, "USD")));

        IncomingPost post = source.emit("Загубив ключі з брелоком, винагорода 500$, " + phone.national());

        IngestPost row = settled(post);
        assertThat(row.getStatus()).isEqualTo(PostStatus.FAILED);
        assertThat(row.getItemId()).isNull();
    }

    private IngestPost settled(IncomingPost post) {
        PostKey key = post.key();
        return await().atMost(EVENTUALLY).until(
                () -> posts.findBySourceAndChannelAndExternalId(key.source(), key.channel(), key.id())
                        .filter(row -> row.getStatus() != PostStatus.PUBLISHING),
                Optional::isPresent).orElseThrow();
    }

    private SiteItem onTheSite(long itemId) {
        return jdbc.queryForObject("""
                select i.title, i.date, i.compensation, i.currency, c.key, p.name, p.country_code,
                       ci.phone, ci.email, cardinality(ci.social_medias) as social_medias
                from lost_item i
                join thing_category c on c.id = i.category_id
                join place p on p.google_place_id = i.place_google_place_id
                join contact_info ci on ci.id = i.info_id
                where i.id = ?
                """, (rs, n) -> new SiteItem(rs.getString("title"), rs.getObject("date", LocalDate.class),
                rs.getObject("compensation", Integer.class), rs.getString("currency"), rs.getString("key"),
                rs.getString("name"), rs.getString("country_code"), rs.getString("phone"), rs.getString("email"),
                rs.getInt("social_medias")), itemId);
    }

    private void resolvesTo(ResolvedPlace place) {
        when(places.resolve(any(), any(), any())).thenReturn(Optional.of(place));
    }

    private void extracts(Extraction extraction) {
        when(extractor.extract(any())).thenReturn(
                new ExtractionResult(extraction, "test/model", null, Duration.ZERO));
    }

    private static Extraction lost(String title, Money compensation) {
        return new Extraction(true, ItemKind.LOST, title, "Загубив біля Ратуші", null, "WALLET", today(), "біля Ратуші",
                "Львів", compensation, "PHONE_1", List.of(SocialMedia.VIBER), 0.9, null);
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    record SiteItem(String title, LocalDate date, Integer compensation, String currency, String category,
                    String place, String countryCode, String phone, String email, int socialMedias) {
    }

    record Phone(String digits) {

        static Phone random() {
            return new Phone("%07d".formatted(ThreadLocalRandom.current().nextInt(10_000_000)));
        }

        String national() {
            return "067 %s %s %s".formatted(digits.substring(0, 3), digits.substring(3, 5), digits.substring(5));
        }

        String e164() {
            return "+38067" + digits;
        }
    }

    static class StackSource implements PostSource {

        static final String NAME = "stack";

        private volatile Consumer<IncomingPost> sink;

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public void start(Consumer<IncomingPost> sink) {
            this.sink = sink;
        }

        @Override
        public byte[] download(PhotoRef photo) {
            throw new UnsupportedOperationException("posts of this source have no photos");
        }

        @Override
        public Optional<String> authorPhone(IncomingPost post) {
            return Optional.empty();
        }

        @Override
        public Optional<AuthorProfile> authorProfile(IncomingPost post) {
            return Optional.empty();
        }

        IncomingPost emit(String text) {
            IncomingPost post = new IncomingPost(new PostKey(NAME, "it", UUID.randomUUID().toString()),
                    "Загублено Львів", null, Instant.now(), text, List.of(), LVIV, null);
            sink.accept(post);
            return post;
        }
    }

    @TestConfiguration
    static class StackSourceConfig {

        @Bean
        StackSource stackSource() {
            return new StackSource();
        }
    }
}
