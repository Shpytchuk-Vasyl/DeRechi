package org.shpytchuk.ingest.llm;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.api.ai.llm.Extraction;
import org.shpytchuk.ingest.api.ai.llm.ExtractionDecision;
import org.shpytchuk.ingest.api.ai.llm.ExtractionPolicy;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.api.ai.model.ItemKind;
import org.shpytchuk.ingest.api.ai.model.Outcome;
import org.shpytchuk.ingest.phone.MaskedText;
import org.shpytchuk.ingest.source.SourceSettings;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionPolicyTest {

    private static final String PHONE_1 = "+380671234567";
    private static final String PHONE_2 = "+380501112233";
    private static final String AUTHOR = "+380939998877";
    private static final SourceSettings LVIV = new SourceSettings("UA", "Львів", "uk");
    private static final SourceSettings NO_CITY = new SourceSettings("UA", null, "uk");

    private final ExtractionPolicy policy = new ExtractionPolicy(
            new IngestProperties(null, "hash", null, 0, null, null, null, 0.6));

    private static Extraction extraction(@Nullable Boolean isNotice, @Nullable ItemKind kind, @Nullable String title,
                                         @Nullable String category, @Nullable String placeText, @Nullable String city,
                                         @Nullable String phoneRef, @Nullable Double confidence, @Nullable String rejectReason) {
        return new Extraction(isNotice, kind, title, "Опис", category, LocalDate.parse("2026-10-08"), placeText, city,
                null, phoneRef, null, confidence, rejectReason);
    }

    private static Extraction good() {
        return extraction(true, ItemKind.FOUND, "Знайдено Леокарту", "DOCUMENTS", "Наукова", null, "PHONE_1", 0.9, null);
    }

    private static MaskedText masked(String... phones) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < phones.length; i++) {
            map.put("PHONE_" + (i + 1), phones[i]);
        }
        return new MaskedText("text", map);
    }

    private ExtractionDecision evaluate(Extraction e, MaskedText masked, @Nullable String author, SourceSettings chat) {
        return policy.evaluate(e, masked, author, chat);
    }

    @Test
    void aGoodExtractionProceedsWithTheReferencedPhone() {
        ExtractionDecision decision = evaluate(good(), masked(PHONE_1, PHONE_2), null, LVIV);

        assertThat(decision.skipped()).isFalse();
        assertThat(decision.contactPhone()).isEqualTo(PHONE_1);
    }

    @Test
    void notANoticeIsSkipped() {
        assertThat(evaluate(extraction(false, null, null, null, null, null, null, 0.9, null), masked(PHONE_1), null, LVIV)
                .skipOutcome()).isEqualTo(Outcome.NOT_NOTICE);
    }

    @Test
    void aNullIsNoticeIsSkippedAsNotANotice() {
        assertThat(evaluate(extraction(null, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.9, null), masked(PHONE_1), null, LVIV)
                .skipOutcome()).isEqualTo(Outcome.NOT_NOTICE);
    }

    @Test
    void aRejectReasonRejects() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.9, "several unrelated items");

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.REJECTED);
    }

    @Test
    void aBlankRejectReasonDoesNotReject() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.9, " ");

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipped()).isFalse();
    }

    @Test
    void lowConfidenceRejects() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.59, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.REJECTED);
    }

    @Test
    void noConfidenceRejects() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, null, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.REJECTED);
    }

    @Test
    void confidenceAtTheThresholdPasses() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.6, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipped()).isFalse();
    }

    @Test
    void noKindIsIncomplete() {
        Extraction e = extraction(true, null, "t", "KEYS", "p", null, "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void aBlankTitleIsIncomplete() {
        Extraction e = extraction(true, ItemKind.LOST, " ", "KEYS", "p", null, "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void noCategoryIsIncomplete() {
        Extraction e = extraction(true, ItemKind.LOST, "t", null, "p", null, "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void noPlaceNoCityAndNoDefaultCityIsIncomplete() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", " ", null, "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, NO_CITY).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void theDefaultCityOfTheChatIsEnoughForAPlace() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", null, null, "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).skipped()).isFalse();
    }

    @Test
    void aCityAloneIsEnoughForAPlace() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", null, "Київ", "PHONE_1", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, NO_CITY).skipped()).isFalse();
    }

    @Test
    void theAuthorsPhoneWinsOverTheText() {
        ExtractionDecision decision = evaluate(good(), masked(PHONE_1, PHONE_2), AUTHOR, LVIV);

        assertThat(decision.contactPhone()).isEqualTo(AUTHOR);
    }

    @Test
    void aReferenceInBracketsIsResolved() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, "[PHONE_2]", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1, PHONE_2), null, LVIV).contactPhone()).isEqualTo(PHONE_2);
    }

    @Test
    void theOnlyNumberInTheTextIsUsedWithoutAReference() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1), null, LVIV).contactPhone()).isEqualTo(PHONE_1);
    }

    @Test
    void severalNumbersWithoutAReferenceAreIncomplete() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, null, 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1, PHONE_2), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void anUnknownReferenceAmongSeveralNumbersIsIncomplete() {
        Extraction e = extraction(true, ItemKind.LOST, "t", "KEYS", "p", null, "PHONE_7", 0.9, null);

        assertThat(evaluate(e, masked(PHONE_1, PHONE_2), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }

    @Test
    void noPhoneAtAllIsIncomplete() {
        assertThat(evaluate(good(), masked(), null, LVIV).skipOutcome()).isEqualTo(Outcome.INCOMPLETE);
    }
}
