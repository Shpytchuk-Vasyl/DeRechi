package org.shpytchuk.adminapi.view;

import java.util.List;

public record MatchRow(ItemView lost, List<CandidateView> candidates, long totalCandidates) {

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public boolean hasMore() {
        return totalCandidates > candidates.size();
    }

    public long hiddenCount() {
        return totalCandidates - candidates.size();
    }
}
