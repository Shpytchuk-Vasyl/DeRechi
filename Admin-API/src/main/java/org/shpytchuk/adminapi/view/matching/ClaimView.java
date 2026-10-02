package org.shpytchuk.adminapi.view.matching;

import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

import java.time.Instant;
import java.util.List;

public record ClaimView(
        String phone,
        String email,
        List<SocialMediaEnum> socialMedias,
        Instant createdAt,
        ClaimStatus status
) {
}
