package org.shpytchuk.telegramingest.pipeline;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.api.DeRechiApi;
import org.shpytchuk.telegramingest.config.IngestProperties;
import org.shpytchuk.telegramingest.entity.PostStatus;
import org.shpytchuk.telegramingest.exception.BudgetExceededException;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.geo.PlaceResolver;
import org.shpytchuk.telegramingest.geo.ResolvedPlace;
import org.shpytchuk.telegramingest.image.ImageRedactor;
import org.shpytchuk.telegramingest.image.RedactedImage;
import org.shpytchuk.telegramingest.image.RedactionMode;
import org.shpytchuk.telegramingest.llm.*;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.Outcome;
import org.shpytchuk.telegramingest.phone.*;
import org.shpytchuk.telegramingest.telegram.IncomingPost;
import org.shpytchuk.telegramingest.telegram.PhotoRef;
import org.shpytchuk.telegramingest.telegram.TelegramSource;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class IngestPipeline {

    private static final String OTHER_CATEGORY = "OTHER";
    private static final String DOCUMENTS_CATEGORY = "DOCUMENTS";

    private final IngestProperties properties;
    private final TelegramSource source;
    private final PhoneExtractor phoneExtractor;
    private final AuthorPhoneResolver authorPhoneResolver;
    private final Masker masker;
    private final PhoneHasher hasher;
    private final ImageRedactor redactor;
    private final Extractor extractor;
    private final ExtractionPolicy policy;
    private final PlaceResolver placeResolver;
    private final DeRechiApi api;
    private final NoticeOutput output;
    private final PostLedger ledger;
    private final IngestMetrics metrics;
    private final Clock clock;

    public void process(IncomingPost post) {
        if (ledger.seen(post)) {
            log.debug("Skipping post {}/{}: already handled", post.chatId(), post.messageId());
            return;
        }
        try {
            run(post);
        } catch (StageUnavailableException e) {
            metrics.outcome(Outcome.UNAVAILABLE);
            log.warn("Post {}/{} left for the next catch-up: {}", post.chatId(), post.messageId(), e.getMessage());
        } catch (BudgetExceededException e) {
            finish(post, Outcome.BUDGET, null);
            log.warn("Post {}/{} skipped: {}", post.chatId(), post.messageId(), e.getMessage());
        } catch (IngestException e) {
            ledger.record(post, PostStatus.FAILED, null, null);
            metrics.outcome(Outcome.FAILED);
            log.warn("Post {}/{} failed: {}", post.chatId(), post.messageId(), e.getMessage());
        }
    }

    private void run(IncomingPost post) {
        // =========== step: find the contact phone ===========
        String region = post.chat().region();
        List<FoundPhone> phones = phoneExtractor.find(post.text(), region);
        String authorPhone = null;
        if (phones.isEmpty()) {
            authorPhone = authorPhoneResolver.resolve(post).orElse(null);
            if (authorPhone == null) {
                metrics.outcome(Outcome.NO_PHONE);
                log.debug("Post {}/{} has no phone number", post.chatId(), post.messageId());
                return;
            }
            metrics.phoneSource("author");
        } else {
            metrics.phoneSource("text");
        }

        // =========== step: skip duplicates ===========
        List<String> candidates = authorPhone != null ? List.of(authorPhone) : phones.stream().map(FoundPhone::e164).distinct().toList();
        List<String> hashes = candidates.stream().map(hasher::hash).toList();
        if (ledger.duplicate(hashes, properties.duplicateWindow())) {
            finish(post, Outcome.DUPLICATE, hashes.getFirst());
            return;
        }

        // =========== step: mask contacts and redact photos ===========
        MaskedText masked = masker.mask(post.text(), phones);
        List<Photo> photos = photos(post);
        Map<String, Long> categories = categories();

        // =========== step: extract the notice with the model ===========
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate postDate = post.postedAt().atZone(post.chat().zone()).toLocalDate();

        ExtractionResult result = extractor.extract(
                new ExtractionRequest(
                        masked.text(),
                        postDate,
                        today,
                        post.chat().defaultCity(),
                        List.copyOf(categories.keySet()),
                        redacted(photos)));
        metrics.llm(result.model(), result.duration(), result.cost());
        Extraction extraction = result.extraction();

        // =========== step: check the extraction ===========
        ExtractionDecision decision = policy.evaluate(extraction, masked, authorPhone, post.chat());
        if (decision.skipped()) {
            skip(post, decision.skipOutcome(), hashes.getFirst(), result, photos);
            return;
        }
        String phoneHash = hasher.hash(decision.contactPhone());

        // =========== step: check category, date and photo ===========
        Long categoryId = categories.getOrDefault(extraction.category(), categories.get(OTHER_CATEGORY));
        if (categoryId == null) {
            skip(post, Outcome.INCOMPLETE, phoneHash, result, photos);
            return;
        }
        LocalDate date = Notices.date(extraction.date(), postDate, today, properties.maxAge());
        if (date == null) {
            skip(post, Outcome.TOO_OLD, phoneHash, result, photos);
            return;
        }
        if (extraction.kind() == ItemKind.FOUND && photos.isEmpty()) {
            skip(post, Outcome.NO_PHOTO, phoneHash, result, photos);
            return;
        }
        // =========== step: find the place ===========
        ResolvedPlace place = place(post, extraction);
        if (place == null) {
            skip(post, Outcome.NO_PLACE, phoneHash, result, photos);
            return;
        }
        if (!api.countries().contains(place.countryCode())) {
            skip(post, Outcome.UNSUPPORTED_COUNTRY, phoneHash, result, photos);
            return;
        }

        // =========== step: hand the notice to the output ===========
        Notice notice = new Notice(extraction.kind(), Notices.cut(extraction.title(), Notices.TITLE_MAX),
                Notices.cut(extraction.description(), Notices.DESCRIPTION_MAX), date,
                Notices.compensation(extraction.compensation()), categoryId, place, decision.contactPhone(),
                Notices.socialMedias(extraction.socialMedias(), authorPhone != null),
                publicPhoto(photos, DOCUMENTS_CATEGORY.equals(extraction.category())), phoneHash);

        output.accept(post, notice, result, redacted(photos));
    }

    private @Nullable ResolvedPlace place(IncomingPost post, Extraction extraction) {
        try {
            return placeResolver.resolve(extraction.placeText(), extraction.city(), post.chat()).orElse(null);
        } catch (IngestException e) {
            log.warn("Post {}/{} place search failed: {}", post.chatId(), post.messageId(), e.getMessage());
            return null;
        }
    }

    private List<Photo> photos(IncomingPost post) {
        List<Photo> photos = new ArrayList<>();
        boolean documentHint = redactor.mentionsDocument(post.text());
        for (PhotoRef ref : post.photos().stream().limit(properties.maxPhotos()).toList()) {
            byte[] original = source.download(ref);
            RedactedImage redacted = redactor.redact(original, documentHint);
            metrics.redacted(redacted.mode());
            photos.add(new Photo(original, redacted));
        }
        return photos;
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
            throw new StageUnavailableException("Client-API is unreachable: " + e.getMessage());
        }
    }

    private void skip(IncomingPost post, Outcome outcome, String phoneHash, ExtractionResult result, List<Photo> photos) {
        finish(post, outcome, phoneHash);
        output.skipped(post, outcome, result, redacted(photos));
    }

    private void finish(IncomingPost post, Outcome outcome, @Nullable String phoneHash) {
        ledger.record(post, PostStatus.SKIPPED, phoneHash, null);
        metrics.outcome(outcome);
        log.info("Post {}/{} skipped: {}", post.chatId(), post.messageId(), outcome.tag());
    }

    private static List<byte[]> redacted(List<Photo> photos) {
        return photos.stream().map(p -> p.redacted().jpeg()).toList();
    }

    private record Photo(byte[] original, RedactedImage redacted) {
    }
}
