package org.shpytchuk.clientapi.dto;

public record ClaimDto(Long id, boolean repeated, String paymentVariantId, boolean paid,
                       boolean contactsSent) {
}
