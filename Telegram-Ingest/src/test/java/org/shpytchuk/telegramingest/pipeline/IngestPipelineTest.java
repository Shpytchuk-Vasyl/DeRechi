package org.shpytchuk.telegramingest.pipeline;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.telegramingest.api.DeRechiApi;
import org.shpytchuk.telegramingest.config.IngestProperties;
import org.shpytchuk.telegramingest.config.IngestProperties.IngestMode;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.geo.PlaceResolver;
import org.shpytchuk.telegramingest.geo.ResolvedPlace;
import org.shpytchuk.telegramingest.image.ImageRedactor;
import org.shpytchuk.telegramingest.image.RedactedImage;
import org.shpytchuk.telegramingest.image.RedactionMode;
import org.shpytchuk.telegramingest.llm.Extraction;
import org.shpytchuk.telegramingest.llm.ExtractionDecision;
import org.shpytchuk.telegramingest.llm.ExtractionPolicy;
import org.shpytchuk.telegramingest.llm.ExtractionRequest;
import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.llm.Extractor;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.model.SocialMedia;
import org.shpytchuk.telegramingest.phone.AuthorPhoneResolver;
import org.shpytchuk.telegramingest.phone.Masker;
import org.shpytchuk.telegramingest.phone.PhoneExtractor;
import org.shpytchuk.telegramingest.phone.PhoneHasher;
import org.shpytchuk.telegramingest.telegram.ChatSettings;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.shpytchuk.telegramingest.telegram.PhotoRef;
import org.shpytchuk.telegramingest.telegram.TelegramSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestPipelineTest {

    static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    static final ChatSettings LVIV = new ChatSettings("UA", "Львів", "uk");
    static final ResolvedPlace PLACE = new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA");
    static final byte[] ORIGINAL = {1};
    static final byte[] REDACTED = {2};
    static final byte[] BLURRED = {3};

    TelegramSource source = mock(TelegramSource.class);
    AuthorPhoneResolver authorPhones = mock(AuthorPhoneResolver.class);
    ImageRedactor redactor = mock(ImageRedactor.class);
    Extractor extractor = mock(Extractor.class);
    ExtractionPolicy policy = mock(ExtractionPolicy.class);
    PlaceResolver places = mock(PlaceResolver.class);
    DeRechiApi api = mock(DeRechiApi.class);
    NoticeOutput output = mock(NoticeOutput.class);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void defaults() {
        when(api.categories()).thenReturn(Map.of("WALLET", 2L, "DOCUMENTS", 1L, "OTHER", 8L));
        when(api.countries()).thenReturn(Set.of("UA", "PL"));
        when(places.resolve(any(), any(), any())).thenReturn(Optional.of(PLACE));
        when(source.download(any())).thenReturn(ORIGINAL);
        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.NONE));
        when(redactor.blurFully(any())).thenReturn(new RedactedImage(BLURRED, RedactionMode.FULL));
        when(policy.evaluate(any(), any(), any(), any())).thenReturn(ExtractionDecision.proceed("+380671234567"));
    }

    @Test
    void postWithoutAnyPhoneLeavesNoTrace() {
        when(authorPhones.resolve(any())).thenReturn(Optional.empty());

        pipeline().process(post("Знайшов ключі, пишіть @someone", List.of()));

        verifyNoInteractions(extractor, output);
        verify(ledger, never()).record(any(), any(), any(), any());
        assertThat(count("no_phone")).isEqualTo(1);
    }

    @Test
    void phoneFromTheAuthorsProfileIsEnough() {
        when(authorPhones.resolve(any())).thenReturn(Optional.of("+380671234567"));
        extracts(extraction(ItemKind.LOST, "WALLET"));

        pipeline().process(post("Загубив гаманець, пишіть @someone", List.of()));

        Notice notice = accepted();
        assertThat(notice.phone()).isEqualTo("+380671234567");
        assertThat(notice.socialMedias()).contains(SocialMedia.TELEGRAM);
    }

    @Test
    void duplicateStopsBeforeTheModel() {
        when(ledger.duplicate(any(), any())).thenReturn(true);

        pipeline().process(post("Знайшов гаманець 067 123 45 67", List.of()));

        verifyNoInteractions(extractor, output);
        verify(ledger).record(any(), eq(PostStatus.SKIPPED), any(), isNull());
        assertThat(count("duplicate")).isEqualTo(1);
    }

    @Test
    void textSentToTheModelHasNoPhone() {
        extracts(extraction(ItemKind.LOST, "WALLET"));

        pipeline().process(post("Загубив гаманець, 067 123 45 67", List.of()));

        ArgumentCaptor<ExtractionRequest> request = ArgumentCaptor.forClass(ExtractionRequest.class);
        verify(extractor).extract(request.capture());
        assertThat(request.getValue().maskedText()).contains("[PHONE_1]").doesNotContain("067");
        assertThat(request.getValue().postDate()).isEqualTo(TODAY);
    }

    @Test
    void readyNoticeGoesToTheOutputWithTheRedactedPhotos() {
        extracts(extraction(ItemKind.FOUND, "WALLET"));

        pipeline().process(post("Знайшов гаманець 067 123 45 67", List.of(new PhotoRef(1, 800, 600))));

        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        ArgumentCaptor<List<byte[]>> photos = ArgumentCaptor.captor();
        verify(output).accept(any(), notice.capture(), any(), photos.capture());
        assertThat(notice.getValue().kind()).isEqualTo(ItemKind.FOUND);
        assertThat(notice.getValue().categoryId()).isEqualTo(2L);
        assertThat(notice.getValue().place()).isEqualTo(PLACE);
        assertThat(notice.getValue().photo()).isEqualTo(REDACTED);
        assertThat(notice.getValue().phoneHash()).hasSize(64);
        assertThat(photos.getValue()).containsExactly(REDACTED);
        verify(ledger, never()).record(any(), any(), any(), any());
    }

    @Test
    void skipAfterTheModelIsRecordedAndReported() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(policy.evaluate(any(), any(), any(), any())).thenReturn(ExtractionDecision.skip(Outcome.NOT_NOTICE));

        pipeline().process(post("Продам велосипед 067 123 45 67", List.of()));

        verify(ledger).record(any(), eq(PostStatus.SKIPPED), any(), isNull());
        verify(output).skipped(any(), eq(Outcome.NOT_NOTICE), any(), any());
        verify(output, never()).accept(any(), any(), any(), any());
        assertThat(count("not_notice")).isEqualTo(1);
    }

    @Test
    void unconfiguredStageLeavesThePostForTheNextCatchUp() {
        when(extractor.extract(any())).thenThrow(new StageUnavailableException("OpenRouter key is not configured"));

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        verify(ledger, never()).record(any(), any(), any(), any());
        verifyNoInteractions(output);
        assertThat(count("unavailable")).isEqualTo(1);
    }

    @Test
    void foundItemWithoutPhotoIsSkipped() {
        extracts(extraction(ItemKind.FOUND, "WALLET"));

        pipeline().process(post("Знайшов гаманець 067 123 45 67", List.of()));

        verify(output).skipped(any(), eq(Outcome.NO_PHOTO), any(), any());
        verify(output, never()).accept(any(), any(), any(), any());
    }

    @Test
    void afterMidnightInKyivTheNoticeKeepsTheUtcDateClientApiAccepts() {
        Instant oneThirtyInKyiv = Instant.parse("2026-10-10T22:30:00Z");
        LocalDate kyivDate = LocalDate.of(2026, 10, 11);
        Extraction tomorrowInUtc = new Extraction(true, ItemKind.LOST, "Гаманець", null, "WALLET", kyivDate,
                "вул. Наукова", null, null, "PHONE_1", List.of(), 0.9, null);
        extracts(tomorrowInUtc);

        pipeline(oneThirtyInKyiv).process(post("Загубив гаманець 067 123 45 67", List.of(), oneThirtyInKyiv));

        ArgumentCaptor<ExtractionRequest> request = ArgumentCaptor.forClass(ExtractionRequest.class);
        verify(extractor).extract(request.capture());
        assertThat(request.getValue().postDate()).isEqualTo(kyivDate);
        assertThat(request.getValue().today()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(accepted().date()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void documentMissedByOcrIsBlurredEntirely() {
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS"));

        pipeline().process(post("Знайшов картку 067 123 45 67", List.of(new PhotoRef(1, 800, 600))));

        verify(redactor).blurFully(ORIGINAL);
        assertThat(accepted().photo()).isEqualTo(BLURRED);
    }

    @Test
    void placeOutsideSupportedCountriesIsSkipped() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(places.resolve(any(), any(), any()))
                .thenReturn(Optional.of(new ResolvedPlace("gp-2", "Berlin", 52.5, 13.4, "DE")));

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        verify(output).skipped(any(), eq(Outcome.UNSUPPORTED_COUNTRY), any(), any());
        assertThat(count("unsupported_country")).isEqualTo(1);
    }

    @Test
    void failedPlaceSearchIsSkippedAsNoPlace() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(places.resolve(any(), any(), any())).thenThrow(new IngestException("Google Places (legacy) failed: HTTP 404"));

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        verify(ledger).record(any(), eq(PostStatus.SKIPPED), any(), isNull());
        verify(output).skipped(any(), eq(Outcome.NO_PLACE), any(), any());
        assertThat(count("no_place")).isEqualTo(1);
        assertThat(count("failed")).isZero();
    }

    @Test
    void alreadyHandledPostIsIgnored() {
        when(ledger.seen(any())).thenReturn(true);

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        verifyNoInteractions(extractor, api, output);
        verify(ledger, never()).record(any(), any(), any(), any());
    }

    private Notice accepted() {
        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(output).accept(any(), notice.capture(), any(), any());
        return notice.getValue();
    }

    private void extracts(Extraction extraction) {
        when(extractor.extract(any())).thenReturn(new ExtractionResult(extraction, "{}", "test/model",
                new BigDecimal("0.001"), Duration.ofMillis(800)));
    }

    private static Extraction extraction(ItemKind kind, String category) {
        return new Extraction(true, kind, "Гаманець", "Чорний шкіряний гаманець", category, TODAY, "вул. Наукова",
                null, null, "PHONE_1", List.of(SocialMedia.VIBER), 0.9, null);
    }

    private static IncomingPost post(String text, List<PhotoRef> photos) {
        return post(text, photos, NOW);
    }

    private static IncomingPost post(String text, List<PhotoRef> photos, Instant postedAt) {
        return new IncomingPost(-100L, 10L << 20, "Загублено Львів", "zagubleno911", postedAt, text, photos, null,
                List.of(), List.of(), LVIV);
    }

    private IngestPipeline pipeline() {
        return pipeline(NOW);
    }

    private IngestPipeline pipeline(Instant now) {
        IngestProperties properties = new IngestProperties(IngestMode.DRY_RUN, "key", Duration.ofDays(14), 3, Period.ofDays(30),
                "https://derechi.example", "uk", 0.6);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new IngestPipeline(properties, source, new PhoneExtractor(), authorPhones, new Masker(),
                new PhoneHasher(properties), redactor, extractor, policy, places, api, output, ledger,
                new IngestMetrics(registry, clock, source), clock);
    }

    private double count(String outcome) {
        var counter = registry.find("derechi.ingest.posts").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
