package org.shpytchuk.clientapi.event;

public record ClaimChange(String routingKey, Long claimId) {
}
