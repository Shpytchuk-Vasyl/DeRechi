package org.shpytchuk.adminapi.view;

import java.util.List;

public record MatchRow(ItemView lost, List<CandidateView> candidates) {

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }
}
