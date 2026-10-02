package org.shpytchuk.adminapi.view.detail;

import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

import java.time.LocalDate;
import java.util.List;

public record ItemView(
        Long id,
        String title,
        String description,
        LocalDate date,
        Integer compensation,
        String currency,
        String image,
        String categoryKey,
        String placeName,
        String countryCode,
        Double lat,
        Double lon,
        String phone,
        String email,
        List<SocialMediaEnum> socialMedias
) {
}
