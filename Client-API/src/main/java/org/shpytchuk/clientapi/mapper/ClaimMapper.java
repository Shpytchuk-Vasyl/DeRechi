package org.shpytchuk.clientapi.mapper;

import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.Claim;

public final class ClaimMapper {

    private ClaimMapper() {
    }

    public static ClaimDto toDto(Claim<?> claim, boolean repeated) {
        return new ClaimDto(
                claim.getId(),
                repeated,
                claim.getToken(),
                claim.getPaymentVariantId(),
                claim.getPaidAt() != null,
                claim.getContactsSentAt() != null
        );
    }
}
