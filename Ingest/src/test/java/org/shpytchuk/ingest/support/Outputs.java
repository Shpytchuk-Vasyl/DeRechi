package org.shpytchuk.ingest.support;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.extraction.Extraction;
import org.shpytchuk.ingest.extraction.ExtractionResult;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.IncomingPost;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Outputs {

    public static final String PHONE = "+380671234567";
    public static final String PHONE_HASH = "a".repeat(64);
    public static final AuthorProfile PROFILE =
            new AuthorProfile(SocialMedia.TELEGRAM, "@finder", "https://t.me/finder");

    private Outputs() {
    }

    public static IncomingPost post() {
        return Posts.telegram(10L << 20, "Знайшов гаманець 067 123 45 67", List.of(),
                Instant.parse("2026-10-10T12:00:00Z"));
    }

    public static Notice notice(byte @Nullable [] photo) {
        return new Notice(ItemKind.FOUND, "Гаманець", "Чорний шкіряний гаманець", LocalDate.of(2026, 10, 10), null,
                2L, new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA"), PHONE, null,
                List.of(SocialMedia.VIBER), photo, null, PHONE_HASH);
    }

    public static Notice placeholderNotice(String placeholderPhoto) {
        return new Notice(ItemKind.FOUND, "Паспорт", "Паспорт громадянина України", LocalDate.of(2026, 10, 10), null,
                1L, new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA"), PHONE, null,
                List.of(SocialMedia.VIBER), null, placeholderPhoto, PHONE_HASH);
    }

    public static Notice profileNotice() {
        return new Notice(ItemKind.FOUND, "Гаманець", "Чорний шкіряний гаманець", LocalDate.of(2026, 10, 10), null,
                2L, new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA"), null, PROFILE,
                List.of(SocialMedia.TELEGRAM), null, null, PHONE_HASH);
    }

    public static ExtractionResult result() {
        Extraction extraction = new Extraction(true, ItemKind.FOUND, "Гаманець", null, null, "WALLET", null, null, null,
                null, "PHONE_1", null, 0.9, null);
        return new ExtractionResult(extraction, "test/model", BigDecimal.ZERO, Duration.ZERO);
    }
}
