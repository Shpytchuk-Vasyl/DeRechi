package org.shpytchuk.ingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.client.DeRechiApi;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.entity.PostStatus;
import org.shpytchuk.ingest.exception.ClientApiUnavailableException;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.extraction.*;
import org.shpytchuk.ingest.image.ImageRedactor;
import org.shpytchuk.ingest.image.RedactedImage;
import org.shpytchuk.ingest.image.RedactionMode;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.phone.*;
import org.shpytchuk.ingest.place.PlaceResolver;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.publish.Publishers;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;
import org.shpytchuk.ingest.source.PhotoRef;
import org.shpytchuk.ingest.source.PostSource;
import org.shpytchuk.ingest.source.PostSources;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class IngestPipeline {

    private static final String OTHER_CATEGORY = "OTHER";
    private static final String DOCUMENTS_CATEGORY = "DOCUMENTS";

    private final IngestProperties properties;
    private final PostSources sources;
    private final PhoneExtractor phoneExtractor;
    private final Masker masker;
    private final PhoneHasher hasher;
    private final ImageRedactor redactor;
    private final Extractor extractor;
    private final OwnerNameFinder ownerNames;
    private final ExtractionPolicy policy;
    private final PlaceResolver placeResolver;
    private final DeRechiApi api;
    private final Publishers publishers;
    private final PostLedger ledger;
    private final IngestMetrics metrics;
    private final Clock clock;

    public void process(IncomingPost post) {
        if (ledger.seen(post)) {
            log.debug("Skipping post {}: already handled", post.key());
            return;
        }
        long started = System.nanoTime();
        log.debug("Post {} started: {} photo(s), {} chars", post.key(), post.photos().size(),
                post.text().length());
        try {
            run(post);
            log.debug("Post {} done in {} ms", post.key(), millis(started));
        } catch (IngestException e) {
            ledger.record(post, PostStatus.FAILED, null, null);
            metrics.outcome(Outcome.FAILED);
            log.warn("Post {} failed: {}", post.key(), e.getMessage());
        }
    }

    private void run(IncomingPost post) {
        // =========== step: find the contact phone ===========
         String region = post.settings().region();
        List<FoundPhone> phones = phoneExtractor.find(post.text(), region);
        String authorPhone = null;
        AuthorProfile authorProfile = null;
        if (phones.isEmpty()) {
            PostSource source = sources.of(post);
            authorPhone = source.authorPhone(post).orElse(null);
            authorProfile = authorPhone == null ? source.authorProfile(post).orElse(null) : null;
            if (authorPhone != null) {
                metrics.phoneSource("author");
                log.debug("Post {} phone: the author's", post.key());
            } else if (authorProfile != null) {
                metrics.phoneSource("profile");
                log.debug("Post {} contact: the author's {} profile", post.key(), authorProfile.network());
            } else {
                metrics.outcome(Outcome.NO_PHONE);
                log.debug("Post {} has no phone number and no author profile", post.key());
                return;
            }
        } else {
            metrics.phoneSource("text");
            log.debug("Post {} phone: {} in the text", post.key(), phones.size());
        }

        // =========== step: skip duplicates ===========
        List<String> hashes = contacts(phones, authorPhone, authorProfile).stream().map(hasher::hash).toList();
        if (ledger.duplicate(hashes, properties.duplicateWindow())) {
            finish(post, Outcome.DUPLICATE, hashes.getFirst());
            return;
        }
        log.debug("Post {} is not a duplicate", post.key());

        // =========== step: mask contacts and redact photos ===========
        MaskedText masked = masker.mask(post.text(), phones);
        Map<String, Long> categories = categories();
        Set<String> countries = countries();
        List<Photo> photos = photos(post);

        // =========== step: extract the notice with the model ===========
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate postDate = post.postedAt().atZone(post.settings().zone()).toLocalDate();

        ExtractionResult result = extractor.extract(
                new ExtractionRequest(
                        masked.text(),
                        postDate,
                        today,
                        post.settings().defaultCity(),
                        List.copyOf(categories.keySet()),
                        redacted(photos)));
        metrics.llm(result.model(), result.duration(), result.cost());
        Extraction extraction = result.extraction();
        log.debug("Post {} model {} answered in {} ms: notice {}, kind {}, category {}, confidence {}",
                post.key(), result.model(), result.duration().toMillis(), extraction.isNotice(),
                extraction.kind(), extraction.category(), extraction.confidence());

        // =========== step: check the extraction ===========
        ExtractionDecision decision = policy.evaluate(extraction, masked, authorPhone, authorProfile, post.settings());
        if (decision.skipped()) {
            finish(post, decision.skipOutcome(), hashes.getFirst());
            return;
        }
        String phoneHash = hasher.hash(authorProfile != null ? authorProfile.contactKey() : decision.contactPhone());

        // =========== step: check category, date and photo ===========
        Long categoryId = categories.getOrDefault(extraction.category(), categories.get(OTHER_CATEGORY));
        if (categoryId == null) {
            finish(post, Outcome.INCOMPLETE, phoneHash);
            return;
        }
        LocalDate date = Notices.date(extraction.date(), postDate, today, properties.maxAge());
        if (date == null) {
            finish(post, Outcome.TOO_OLD, phoneHash);
            return;
        }
        String placeholderPhoto = null;
        if (extraction.kind() == ItemKind.FOUND && photos.isEmpty()) {
            placeholderPhoto = properties.placeholderPhoto(extraction.category()).orElse(null);
            if (placeholderPhoto == null) {
                finish(post, Outcome.NO_PHOTO, phoneHash);
                return;
            }
            log.debug("Post {} has no photo, the {} placeholder stands in", post.key(), extraction.category());
        }
        log.debug("Post {} passed the checks: category {}, date {}", post.key(), categoryId, date);

        // =========== step: find the place ===========
        long placeStarted = System.nanoTime();
        ResolvedPlace place = place(post, extraction);
        log.debug("Post {} place search took {} ms: {}", post.key(), millis(placeStarted),
                place == null ? "nothing found" : place.name() + " (" + place.countryCode() + ")");
        if (place == null) {
            finish(post, Outcome.NO_PLACE, phoneHash);
            return;
        }
        if (!countries.contains(place.countryCode())) {
            finish(post, Outcome.UNSUPPORTED_COUNTRY, phoneHash);
            return;
        }

        // =========== step: unblur what the model says is not a document ===========
        boolean document = DOCUMENTS_CATEGORY.equals(extraction.category());
        photos = document ? photos : withoutGuessedDocuments(post, photos);

        // =========== step: read the owner's name off a document the model could not ===========
        String title = extraction.title();
        if (document && !StringUtils.hasText(extraction.ownerName())) {
            title = withOwnerName(post, title, photos);
        }

        // =========== step: hand the notice to the output ===========
        Notice notice = new Notice(extraction.kind(), Notices.cut(title, Notices.TITLE_MAX),
                Notices.cut(extraction.description(), Notices.DESCRIPTION_MAX), date,
                Notices.compensation(extraction.compensation()), categoryId, place, decision.contactPhone(),
                authorProfile, socialMedias(extraction, authorPhone, authorProfile), publicPhoto(photos, document),
                placeholderPhoto, phoneHash);

        publishers.publish(post, notice, result);
    }

    private String withOwnerName(IncomingPost post, String title, List<Photo> photos) {
        List<String> lines = photos.stream().flatMap(photo -> photo.redacted().text().stream()).distinct().toList();
        if (lines.isEmpty()) {
            return title;
        }
        log.debug("Post {}: asking the model for the owner's name with the title and {} line(s) from the photos: {}",
                post.key(), lines.size(), lines);
        try {
            OwnerNameResult result = ownerNames.find(new OwnerNameRequest(title, lines));
            metrics.llm(result.model(), result.duration(), result.cost());
            if (!result.answer().found()) {
                log.debug("Post {}: none of the {} lines on the photos is the owner's name", post.key(), lines.size());
                return title;
            }
            log.debug("Post {}: the owner's name is on a photo, the title carries it now", post.key());
            return result.answer().title();
        } catch (IngestException | StageUnavailableException e) {
            log.warn("Post {}: the owner's name was not looked up, the title stays: {}", post.key(), e.getMessage());
            return title;
        }
    }

    private static List<String> contacts(List<FoundPhone> phones, @Nullable String authorPhone,
                                         @Nullable AuthorProfile authorProfile) {
        if (authorProfile != null) {
            return List.of(authorProfile.contactKey());
        }
        if (authorPhone != null) {
            return List.of(authorPhone);
        }
        return phones.stream().map(FoundPhone::e164).distinct().toList();
    }

    private static List<SocialMedia> socialMedias(Extraction extraction, @Nullable String authorPhone,
                                                  @Nullable AuthorProfile authorProfile) {
        if (authorProfile != null) {
            return List.of(authorProfile.network());
        }
        return Notices.socialMedias(extraction.socialMedias(), authorPhone != null);
    }

    private @Nullable ResolvedPlace place(IncomingPost post, Extraction extraction) {
        try {
            return placeResolver.resolve(extraction.placeText(), extraction.city(), post.settings()).orElse(null);
        } catch (IngestException e) {
            log.warn("Post {} place search failed: {}", post.key(), e.getMessage());
            return null;
        }
    }

    private List<Photo> photos(IncomingPost post) {
        List<Photo> photos = new ArrayList<>();
        boolean documentHint = redactor.mentionsDocument(post.text());
        List<PhotoRef> refs = post.photos().stream().limit(properties.maxPhotos()).toList();
        for (PhotoRef ref : refs) {
            long started = System.nanoTime();
            byte[] original = sources.of(post).download(ref);
            long downloaded = System.nanoTime();
            RedactedImage redacted = redactor.redact(original, documentHint);
            metrics.redacted(redacted.mode());
            photos.add(new Photo(original, redacted));
            log.debug("Post {} photo {}/{}: {} KB downloaded in {} ms, redacted as {} in {} ms", post.key(), photos.size(),
                    refs.size(), original.length / 1024, millis(started, downloaded), redacted.mode(), millis(downloaded));
        }
        return photos;
    }

    private static long millis(long since) {
        return millis(since, System.nanoTime());
    }

    private static long millis(long since, long until) {
        return (until - since) / 1_000_000;
    }

    private byte @Nullable [] publicPhoto(List<Photo> photos, boolean document) {
        if (photos.isEmpty()) {
            return null;
        }
        Photo first = photos.getFirst();
        RedactionMode mode = first.redacted().mode();
        if (document && mode != RedactionMode.DOCUMENT && mode != RedactionMode.FULL) {
            RedactedImage blurred = redactor.blurFully(first.original());
            metrics.redacted(blurred.mode());
            return blurred.jpeg();
        }
        return first.redacted().jpeg();
    }

    private Map<String, Long> categories() {
        try {
            return api.categories();
        } catch (IngestException e) {
            throw clientApiUnreachable(e);
        }
    }

    private Set<String> countries() {
        try {
            return api.countries();
        } catch (IngestException e) {
            throw clientApiUnreachable(e);
        }
    }

    private static ClientApiUnavailableException clientApiUnreachable(IngestException e) {
        return new ClientApiUnavailableException("Client-API is unreachable: " + e.getMessage());
    }

    private void finish(IncomingPost post, Outcome outcome, @Nullable String phoneHash) {
        ledger.record(post, PostStatus.SKIPPED, phoneHash, null);
        metrics.outcome(outcome);
        log.info("Post {} skipped: {}", post.key(), outcome.tag());
    }

    private List<Photo> withoutGuessedDocuments(IncomingPost post, List<Photo> photos) {
        List<Photo> checked = new ArrayList<>();
        for (Photo photo : photos) {
            RedactionMode mode = photo.redacted().mode();
            if (mode != RedactionMode.DOCUMENT && mode != RedactionMode.FULL) {
                checked.add(photo);
                continue;
            }
            RedactedImage again = redactor.redactOnPhotoEvidence(photo.original());
            log.debug("Post {} photo is not a document by the model: redacted as {} instead of {}", post.key(),
                    again.mode(), mode);
            checked.add(new Photo(photo.original(), again));
        }
        return checked;
    }

    private static List<byte[]> redacted(List<Photo> photos) {
        return photos.stream().map(p -> p.redacted().jpeg()).toList();
    }

    private record Photo(byte[] original, RedactedImage redacted) {
    }
}
