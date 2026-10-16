package org.shpytchuk.ingest.extraction;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.model.Outcome;
import org.shpytchuk.ingest.phone.MaskedText;
import org.shpytchuk.ingest.source.AuthorProfile;
import org.shpytchuk.ingest.source.SourceSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@AllArgsConstructor
public class ExtractionPolicy {

    private final IngestProperties properties;

    public ExtractionDecision evaluate(Extraction e, MaskedText masked, @Nullable String authorPhone,
                                       @Nullable AuthorProfile authorProfile, SourceSettings chat) {
        if (!Boolean.TRUE.equals(e.isNotice())) {
            return ExtractionDecision.skip(Outcome.NOT_NOTICE);
        }
        if (StringUtils.hasText(e.rejectReason()) || e.confidence() == null
                || e.confidence() < properties.minConfidence()) {
            return ExtractionDecision.skip(Outcome.REJECTED);
        }
        if (e.kind() == null || !StringUtils.hasText(e.title()) || !StringUtils.hasText(e.category())) {
            return ExtractionDecision.skip(Outcome.INCOMPLETE);
        }
        if (!StringUtils.hasText(e.placeText()) && !StringUtils.hasText(e.city())
                && !StringUtils.hasText(chat.defaultCity())) {
            return ExtractionDecision.skip(Outcome.INCOMPLETE);
        }
        String phone = contactPhone(e, masked, authorPhone);
        if (phone == null && authorProfile == null) {
            return ExtractionDecision.skip(Outcome.INCOMPLETE);
        }
        return ExtractionDecision.proceed(phone);
    }

    private static @Nullable String contactPhone(Extraction e, MaskedText masked, @Nullable String authorPhone) {
        if (authorPhone != null) {
            return authorPhone;
        }
        String referenced = masked.phone(e.contactPhoneRef());
        if (referenced != null) {
            return referenced;
        }
        return masked.phones().size() == 1 ? masked.phones().values().iterator().next() : null;
    }
}
