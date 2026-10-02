package org.shpytchuk.adminapi.view.matching;

import java.time.Instant;
import java.util.Locale;

public enum ClaimStatus {
    NEW,
    REMINDED,
    PAID,
    CONFIRMED;

    public static ClaimStatus of(Instant confirmedAt, Instant paidAt, Instant authorRemindedAt, Instant claimantRemindedAt) {
        if (confirmedAt != null) {
            return CONFIRMED;
        }
        if (paidAt != null) {
            return PAID;
        }
        return authorRemindedAt != null || claimantRemindedAt != null ? REMINDED : NEW;
    }

    public String key() {
        return "claim.status." + name().toLowerCase(Locale.ROOT);
    }
}
