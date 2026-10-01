package org.shpytchuk.adminapi.view;

import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;

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
