package org.shpytchuk.telegramingest.pipeline;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.geo.ResolvedPlace;
import org.shpytchuk.telegramingest.llm.Extraction;
import org.shpytchuk.telegramingest.llm.ExtractionResult;
import org.shpytchuk.telegramingest.model.ItemKind;
import org.shpytchuk.telegramingest.model.SocialMedia;
import org.shpytchuk.telegramingest.telegram.ChatSettings;
import org.shpytchuk.telegramingest.telegram.IncomingPost;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Fixtures shared by the {@link NoticeOutput} tests. */
final class Outputs {

    static final String PHONE = "+380671234567";
    static final String PHONE_HASH = "a".repeat(64);

    private Outputs() {
    }

    static IncomingPost post() {
        return new IncomingPost(-100L, 10L << 20, "Загублено Львів", "zagubleno911",
                Instant.parse("2026-10-10T12:00:00Z"), "Знайшов гаманець 067 123 45 67", List.of(), null,
                List.of(), List.of(), new ChatSettings("UA", "Львів", "uk"));
    }

    static Notice notice(byte @Nullable [] photo) {
        return new Notice(ItemKind.FOUND, "Гаманець", "Чорний шкіряний гаманець", LocalDate.of(2026, 10, 10), null,
                2L, new ResolvedPlace("gp-1", "вул. Наукова", 49.8, 24.0, "UA"), PHONE,
                List.of(SocialMedia.VIBER), photo, PHONE_HASH);
    }

    static ExtractionResult result() {
        Extraction extraction = new Extraction(true, ItemKind.FOUND, "Гаманець", null, "WALLET", null, null, null,
                null, "PHONE_1", null, 0.9, null);
        return new ExtractionResult(extraction, "{\"isNotice\":true}", "test/model", BigDecimal.ZERO, Duration.ZERO);
    }
}
