package org.shpytchuk.adminapi.view;

import java.time.Instant;

public record CandidateView(
        ItemView found,
        Double matchOrder,
        Instant notifiedAt,
        String notifiedBy
) {

    public boolean isNotified() {
        return notifiedAt != null;
    }
}
