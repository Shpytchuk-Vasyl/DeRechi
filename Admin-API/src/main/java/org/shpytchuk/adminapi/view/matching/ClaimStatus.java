package org.shpytchuk.adminapi.view.matching;

import java.time.Instant;
import java.util.Locale;

public enum ClaimStatus {
    NEW,
    REMINDED,
    CONFIRMED;

    public static ClaimStatus of(Instant confirmedAt, Instant authorRemindedAt, Instant claimantRemindedAt) {
        if (confirmedAt != null) {
            return CONFIRMED;
        }
        return authorRemindedAt != null || claimantRemindedAt != null ? REMINDED : NEW;
    }

    public String key() {
        return "claim.status." + name().toLowerCase(Locale.ROOT);
    }
}
