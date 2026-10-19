package org.shpytchuk.ingest.pipeline;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.ingest.client.DeRechiApi;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.exception.BudgetExceededException;
import org.shpytchuk.ingest.exception.ClientApiUnavailableException;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.OpenRouterUnavailableException;
import org.shpytchuk.ingest.extraction.Extraction;
import org.shpytchuk.ingest.extraction.ExtractionDecision;
import org.shpytchuk.ingest.extraction.ExtractionPolicy;
import org.shpytchuk.ingest.extraction.ExtractionRequest;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.extraction.Extractor;
import org.shpytchuk.ingest.extraction.OwnerName;
import org.shpytchuk.ingest.extraction.OwnerNameFinder;
import org.shpytchuk.ingest.extraction.OwnerNameRequest;
import org.shpytchuk.ingest.extraction.OwnerNameResult;
import org.shpytchuk.ingest.image.ImageRedactor;
import org.shpytchuk.ingest.image.RedactedImage;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.phone.Masker;
import org.shpytchuk.ingest.phone.PhoneExtractor;
import org.shpytchuk.ingest.phone.PhoneHasher;
import org.shpytchuk.ingest.place.PlaceResolver;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.publish.Publishers;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostSource;
import org.shpytchuk.ingest.source.PostSources;
import org.shpytchuk.ingest.source.SourceSettings;
import org.shpytchuk.ingest.source.facebook.FacebookActor;
import org.shpytchuk.ingest.source.facebook.FacebookPostSource;
import org.shpytchuk.ingest.source.telegram.TelegramPostSource;
import org.shpytchuk.ingest.source.telegram.TelegramSource;
import org.shpytchuk.ingest.support.Posts;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestPipelineTest {

    static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");
    static final ResolvedPlace PLACE = new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA");
    static final byte[] ORIGINAL = {1};
    static final byte[] REDACTED = {2};
    static final byte[] BLURRED = {3};
    static final AuthorProfile TELEGRAM_PROFILE =
            new AuthorProfile(SocialMedia.TELEGRAM, "@finder", "https://t.me/finder");

    PostSource source = mock(PostSource.class);
    PostSource facebook = mock(PostSource.class);
    ImageRedactor redactor = mock(ImageRedactor.class);
    Extractor extractor = mock(Extractor.class);
    OwnerNameFinder ownerNames = mock(OwnerNameFinder.class);
    ExtractionPolicy policy = mock(ExtractionPolicy.class);
    PlaceResolver places = mock(PlaceResolver.class);
    DeRechiApi api = mock(DeRechiApi.class);
    Publishers output = mock(Publishers.class);
    PostLedger ledger = mock(PostLedger.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PhoneHasher hasher = new PhoneHasher(properties());

    @BeforeEach
    void defaults() {
        when(api.categories()).thenReturn(Map.of("WALLET", 2L, "DOCUMENTS", 1L, "OTHER", 8L));
        when(api.countries()).thenReturn(Set.of("UA", "PL"));
        when(places.resolve(any(), any(), any())).thenReturn(Optional.of(PLACE));
        when(source.name()).thenReturn(TelegramPostSource.NAME);
        when(facebook.name()).thenReturn(FacebookPostSource.NAME);
        when(source.download(any())).thenReturn(ORIGINAL);
        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.NONE));
        when(redactor.blurFully(any())).thenReturn(new RedactedImage(BLURRED, RedactionMode.FULL));
        when(policy.evaluate(any(), any(), any(), any(), any())).thenReturn(ExtractionDecision.proceed("+380671234567"));
    }

    @Test
    void postWithoutAPhoneOrAnAuthorProfileLeavesNoTrace() {
        when(source.authorPhone(any())).thenReturn(Optional.empty());
        when(source.authorProfile(any())).thenReturn(Optional.empty());

        pipeline().process(post("Знайшов ключі, пишіть @someone", List.of()));

        verify(source).authorProfile(any());
        verifyNoInteractions(extractor, output);
        verify(ledger, never()).record(any(), any(), any(), any());
        assertThat(count("no_phone")).isEqualTo(1);
    }

    @Test
    void phoneFromTheAuthorsProfileIsEnough() {
        when(source.authorPhone(any())).thenReturn(Optional.of("+380671234567"));
        extracts(extraction(ItemKind.LOST, "WALLET"));

        pipeline().process(post("Загубив гаманець, пишіть @someone", List.of()));

        Notice notice = accepted();
        assertThat(notice.phone()).isEqualTo("+380671234567");
        assertThat(notice.authorProfile()).isNull();
        assertThat(notice.socialMedias()).contains(SocialMedia.TELEGRAM);
        verify(source, never()).authorProfile(any());
    }

    @Test
    void postWithoutAPhoneGoesOnWithTheAuthorsTelegramProfile() {
        when(source.authorProfile(any())).thenReturn(Optional.of(TELEGRAM_PROFILE));
        when(policy.evaluate(any(), any(), isNull(), eq(TELEGRAM_PROFILE), any()))
                .thenReturn(ExtractionDecision.proceed(null));
        extracts(extraction(ItemKind.LOST, "WALLET"));

        pipeline().process(post("Загубив гаманець біля ратуші, пишіть мені", List.of()));

        Notice notice = accepted();
        assertThat(notice.phone()).isNull();
        assertThat(notice.authorProfile()).isEqualTo(TELEGRAM_PROFILE);
        assertThat(notice.socialMedias()).containsExactly(SocialMedia.TELEGRAM);
        assertThat(notice.phoneHash()).isEqualTo(hasher.hash("TELEGRAM:@finder"));
        assertThat(phoneSource("profile")).isEqualTo(1);
    }

    @Test
    void facebookPostWithoutAPhoneGoesOnWithOnlyItsAuthorsFacebookProfile() {
        AuthorProfile profile = new AuthorProfile(SocialMedia.FACEBOOK, "Олена Коваль",
                "https://www.facebook.com/olena.koval.101");
        when(facebook.authorProfile(any())).thenReturn(Optional.of(profile));
        when(policy.evaluate(any(), any(), isNull(), eq(profile), any())).thenReturn(ExtractionDecision.proceed(null));
        extracts(extraction(ItemKind.LOST, "WALLET"));

        pipeline().process(Posts.facebook("9002", "Загубили ключі на проспекті Злуки",
                new FacebookActor("Олена Коваль", "https://www.facebook.com/olena.koval.101"), NOW));

        Notice notice = accepted();
        assertThat(notice.phone()).isNull();
        assertThat(notice.authorProfile()).isEqualTo(profile);
        assertThat(notice.socialMedias()).containsExactly(SocialMedia.FACEBOOK);
        assertThat(notice.phoneHash()).isEqualTo(hasher.hash("FACEBOOK:олена коваль"));
        verify(source, never()).authorProfile(any());
    }

    @Test
    void aRepostByTheSameProfileIsADuplicateWhateverTheCaseOfTheNickname() {
        when(source.authorProfile(any())).thenReturn(
                Optional.of(new AuthorProfile(SocialMedia.TELEGRAM, "@Finder", "https://t.me/Finder")));
        String hash = hasher.hash("TELEGRAM:@finder");
        when(ledger.duplicate(eq(List.of(hash)), any())).thenReturn(true);

        pipeline().process(post("Знайшов гаманець біля ратуші, пишіть", List.of()));

        verifyNoInteractions(extractor, output);
        verify(ledger).record(any(), eq(PostStatus.SKIPPED), eq(hash), isNull());
        assertThat(count("duplicate")).isEqualTo(1);
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

        pipeline().process(post("Знайшов гаманець 067 123 45 67", List.of(new PhotoRef("1", 800, 600))));

        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(output).publish(any(), notice.capture(), any());
        assertThat(notice.getValue().kind()).isEqualTo(ItemKind.FOUND);
        assertThat(notice.getValue().categoryId()).isEqualTo(2L);
        assertThat(notice.getValue().place()).isEqualTo(PLACE);
        assertThat(notice.getValue().photo()).isEqualTo(REDACTED);
        assertThat(notice.getValue().phoneHash()).hasSize(64);
        verify(ledger, never()).record(any(), any(), any(), any());
    }

    @Test
    void skipAfterTheModelIsRecorded() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(policy.evaluate(any(), any(), any(), any(), any())).thenReturn(ExtractionDecision.skip(Outcome.NOT_NOTICE));

        pipeline().process(post("Продам велосипед 067 123 45 67", List.of()));

        verify(ledger).record(any(), eq(PostStatus.SKIPPED), any(), isNull());
        verify(output, never()).publish(any(), any(), any());
        assertThat(count("not_notice")).isEqualTo(1);
    }

    @Test
    void anUnavailableStageIsHandedBackWithoutARecord() {
        OpenRouterUnavailableException unavailable =
                new OpenRouterUnavailableException("OpenRouter API key is not configured");
        when(extractor.extract(any())).thenThrow(unavailable);

        assertThatThrownBy(() -> pipeline().process(post("Загубив гаманець 067 123 45 67", List.of())))
                .isSameAs(unavailable);
        verify(ledger, never()).record(any(), any(), any(), any());
        verifyNoInteractions(output);
    }

    @Test
    void anUnreachableClientApiIsAnUnavailableStageBeforePhotosAndTheModel() {
        when(api.countries()).thenThrow(new IngestException("countries failed: java.net.ConnectException"));

        assertThatThrownBy(() -> pipeline().process(
                post("Знайшов гаманець 067 123 45 67", List.of(new PhotoRef("1", 800, 600)))))
                .isInstanceOf(ClientApiUnavailableException.class);
        verifyNoInteractions(extractor, redactor, output);
        verify(ledger, never()).record(any(), any(), any(), any());
    }

    @Test
    void anOverBudgetPostIsHandedBackWithoutARecord() {
        BudgetExceededException budget = new BudgetExceededException("The daily limit is used up",
                Instant.parse("2026-10-11T00:00:00Z"));
        when(extractor.extract(any())).thenThrow(budget);

        assertThatThrownBy(() -> pipeline().process(post("Загубив гаманець 067 123 45 67", List.of())))
                .isSameAs(budget);
        verify(ledger, never()).record(any(), any(), any(), any());
        verifyNoInteractions(output);
    }

    @Test
    void foundItemWithoutPhotoIsSkipped() {
        extracts(extraction(ItemKind.FOUND, "WALLET"));

        pipeline().process(post("Знайшов гаманець 067 123 45 67", List.of()));

        assertThat(count("no_photo")).isEqualTo(1);
        verify(output, never()).publish(any(), any(), any());
    }

    @Test
    void foundDocumentWithoutPhotoTakesThePlaceholder() {
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS"));

        pipeline().process(post("Знайшов паспорт 067 123 45 67", List.of()));

        Notice notice = accepted();
        assertThat(notice.photo()).isNull();
        assertThat(notice.placeholderPhoto()).isEqualTo("placeholders/documents.jpg");
        assertThat(count("no_photo")).isZero();
        verifyNoInteractions(ownerNames);
    }

    @Test
    void aLostItemWithoutPhotoNeedsNoPlaceholder() {
        extracts(extraction(ItemKind.LOST, "DOCUMENTS"));

        pipeline().process(post("Загубив паспорт 067 123 45 67", List.of()));

        assertThat(accepted().placeholderPhoto()).isNull();
    }

    @Test
    void theOwnersNameIsReadOffTheDocumentPhotoWhenTheModelDidNotGiveOne() {
        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.DOCUMENT,
                List.of("ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ", "ШЕВЧЕНКО", "ТАРАС")));
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS"));
        when(ownerNames.find(any())).thenReturn(new OwnerNameResult(
                new OwnerName("Шевченко Тарас", "Знайдено паспорт на ім'я Шевченко Тарас"), "test/model", null,
                Duration.ofMillis(300)));

        pipeline().process(post("Знайшов паспорт 067 123 45 67", List.of(new PhotoRef("1", 800, 600))));

        ArgumentCaptor<OwnerNameRequest> request = ArgumentCaptor.forClass(OwnerNameRequest.class);
        verify(ownerNames).find(request.capture());
        assertThat(request.getValue().title()).isEqualTo("Гаманець");
        assertThat(request.getValue().photoText()).containsExactly("ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ", "ШЕВЧЕНКО", "ТАРАС");
        assertThat(accepted().title()).isEqualTo("Знайдено паспорт на ім'я Шевченко Тарас");
    }

    @Test
    void theTitleStaysWhenThePhotoNamesNobodyOrTheLookupFails() {
        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.DOCUMENT,
                List.of("ПАСПОРТ ГРОМАДЯНИНА УКРАЇНИ")));
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS"));
        when(ownerNames.find(any()))
                .thenReturn(new OwnerNameResult(new OwnerName(null, "Гаманець"), "test/model", null, Duration.ZERO))
                .thenThrow(new OpenRouterUnavailableException("OpenRouter did not take the request: HTTP 503"));

        pipeline().process(post("Знайшов паспорт 067 123 45 67", List.of(new PhotoRef("1", 800, 600))));
        pipeline().process(post("Знайшов паспорт 067 123 45 68", List.of(new PhotoRef("2", 800, 600))));

        ArgumentCaptor<Notice> notices = ArgumentCaptor.forClass(Notice.class);
        verify(output, times(2)).publish(any(), notices.capture(), any());
        assertThat(notices.getAllValues()).extracting(Notice::title).containsExactly("Гаманець", "Гаманець");
        assertThat(count("failed")).isZero();
    }

    @Test
    void theOwnersNameIsNotLookedUpWhenTheModelGaveItOrThePhotoHasNoTextOrItIsNotADocument() {
        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.DOCUMENT,
                List.of("ШЕВЧЕНКО")));
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS", "Шевченко Тарас"));
        pipeline().process(post("Знайшов паспорт 067 123 45 67", List.of(new PhotoRef("1", 800, 600))));

        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.FULL));
        extracts(extraction(ItemKind.FOUND, "DOCUMENTS"));
        pipeline().process(post("Знайшов паспорт 067 123 45 68", List.of(new PhotoRef("2", 800, 600))));

        when(redactor.redact(any(), anyBoolean())).thenReturn(new RedactedImage(REDACTED, RedactionMode.NONE,
                List.of("ШЕВЧЕНКО")));
        extracts(extraction(ItemKind.FOUND, "WALLET"));
        pipeline().process(post("Знайшов гаманець 067 123 45 69", List.of(new PhotoRef("3", 800, 600))));

        verifyNoInteractions(ownerNames);
    }

    @Test
    void afterMidnightInKyivTheNoticeKeepsTheUtcDateClientApiAccepts() {
        Instant oneThirtyInKyiv = Instant.parse("2026-10-10T22:30:00Z");
        LocalDate kyivDate = LocalDate.of(2026, 10, 11);
        Extraction tomorrowInUtc = new Extraction(true, ItemKind.LOST, "Гаманець", null, null, "WALLET", kyivDate,
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

        pipeline().process(post("Знайшов картку 067 123 45 67", List.of(new PhotoRef("1", 800, 600))));

        verify(redactor).blurFully(ORIGINAL);
        assertThat(accepted().photo()).isEqualTo(BLURRED);
    }

    @Test
    void placeOutsideSupportedCountriesIsSkipped() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(places.resolve(any(), any(), any()))
                .thenReturn(Optional.of(new ResolvedPlace("gp-2", "Berlin", 52.5, 13.4, "DE")));

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        assertThat(count("unsupported_country")).isEqualTo(1);
    }

    @Test
    void failedPlaceSearchIsSkippedAsNoPlace() {
        extracts(extraction(ItemKind.LOST, "WALLET"));
        when(places.resolve(any(), any(), any())).thenThrow(new IngestException("Google Places (legacy) failed: HTTP 404"));

        pipeline().process(post("Загубив гаманець 067 123 45 67", List.of()));

        verify(ledger).record(any(), eq(PostStatus.SKIPPED), any(), isNull());
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
        verify(output).publish(any(), notice.capture(), any());
        return notice.getValue();
    }

    private void extracts(Extraction extraction) {
        when(extractor.extract(any())).thenReturn(new ExtractionResult(extraction, "test/model",
                new BigDecimal("0.001"), Duration.ofMillis(800)));
    }

    private static Extraction extraction(ItemKind kind, String category) {
        return extraction(kind, category, null);
    }

    private static Extraction extraction(ItemKind kind, String category, @Nullable String ownerName) {
        return new Extraction(true, kind, "Гаманець", "Чорний шкіряний гаманець", ownerName, category, TODAY,
                "вул. Наукова", null, null, "PHONE_1", List.of(SocialMedia.VIBER), 0.9, null);
    }

    private static IncomingPost post(String text, List<PhotoRef> photos) {
        return post(text, photos, NOW);
    }

    private static IncomingPost post(String text, List<PhotoRef> photos, Instant postedAt) {
        return Posts.telegram(10L << 20, text, photos, postedAt);
    }

    private IngestPipeline pipeline() {
        return pipeline(NOW);
    }

    private IngestPipeline pipeline(Instant now) {
        IngestProperties properties = properties();
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new IngestPipeline(properties, new PostSources(List.of(source, facebook)), new PhoneExtractor(),
                new Masker(), new PhoneHasher(properties), redactor, extractor, ownerNames, policy, places, api, output,
                ledger, new IngestMetrics(registry, clock, mock(TelegramSource.class)), clock);
    }

    private static IngestProperties properties() {
        return new IngestProperties(List.of(), "key", Duration.ofDays(14), 3, Period.ofDays(30),
                "https://derechi.example", "uk", 0.6, Map.of("DOCUMENTS", "placeholders/documents.jpg"));
    }

    private double count(String outcome) {
        var counter = registry.find("derechi.ingest.posts").tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private double phoneSource(String source) {
        var counter = registry.find("derechi.ingest.phone.source").tag("source", source).counter();
        return counter == null ? 0 : counter.count();
    }
}
