package org.shpytchuk.clientapi.util;

import com.sanctionco.jmail.Email;
import com.sanctionco.jmail.JMail;
import com.sanctionco.jmail.normalization.CaseOption;
import com.sanctionco.jmail.normalization.NormalizationOptions;

import java.util.Locale;
import java.util.Set;

public final class EmailNormalizer {

    private static final Set<String> GMAIL = Set.of("gmail.com", "googlemail.com");

    private static final NormalizationOptions ANY_DOMAIN = NormalizationOptions.builder()
            .adjustCase(CaseOption.NO_CHANGE)
            .removeSubAddress()
            .build();

    private static final NormalizationOptions GMAIL_DOMAIN = NormalizationOptions.builder()
            .adjustCase(CaseOption.NO_CHANGE)
            .removeSubAddress()
            .removeDots()
            .build();

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return JMail.tryParse(email.strip())
                .map(EmailNormalizer::normalize)
                .orElse(email);
    }

    private static String normalize(Email email) {
        if (!GMAIL.contains(email.domain().toLowerCase(Locale.ROOT))) {
            return email.normalized(ANY_DOMAIN).toLowerCase(Locale.ROOT);
        }
        String normalized = email.normalized(GMAIL_DOMAIN).toLowerCase(Locale.ROOT);
        return normalized.substring(0, normalized.lastIndexOf('@')) + "@gmail.com";
    }
}
