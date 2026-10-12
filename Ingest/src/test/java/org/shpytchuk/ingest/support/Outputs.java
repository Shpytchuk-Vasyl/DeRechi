package org.shpytchuk.ingest.support;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.api.geo.ResolvedPlace;
import org.shpytchuk.ingest.api.ai.llm.Extraction;
import org.shpytchuk.ingest.api.ai.llm.ExtractionResult;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.api.ai.model.SocialMedia;
import org.shpytchuk.ingest.pipeline.Notice;
import org.shpytchuk.ingest.source.IncomingPost;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Outputs {

    public static final String PHONE = "+380671234567";
    public static final String PHONE_HASH = "a".repeat(64);

    private Outputs() {
    }

    public static IncomingPost post() {
        return Posts.telegram(10L << 20, "Знайшов гаманець 067 123 45 67", List.of(),
                Instant.parse("2026-10-10T12:00:00Z"));
    }

    public static Notice notice(byte @Nullable [] photo) {
        return new Notice(ItemKind.FOUND, "Гаманець", "Чорний шкіряний гаманець", LocalDate.of(2026, 10, 10), null,
                2L, new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA"), PHONE,
                List.of(SocialMedia.VIBER), photo, PHONE_HASH);
    }

    public static ExtractionResult result() {
        Extraction extraction = new Extraction(true, ItemKind.FOUND, "Гаманець", null, "WALLET", null, null, null,
                null, "PHONE_1", null, 0.9, null);
        return new ExtractionResult(extraction, "{\"isNotice\":true}", "test/model", BigDecimal.ZERO, Duration.ZERO);
    }
}
