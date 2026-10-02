package org.shpytchuk.adminapi.view.matching;

import org.shpytchuk.adminapi.view.detail.ItemView;

public record NotifiedMatch(ItemView lost, CandidateView candidate) {
}
