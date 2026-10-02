package org.shpytchuk.adminapi.view.matching;

import org.shpytchuk.adminapi.view.detail.ItemView;

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
