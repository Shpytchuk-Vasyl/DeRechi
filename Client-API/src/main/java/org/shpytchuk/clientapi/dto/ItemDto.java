package org.shpytchuk.clientapi.dto;

import java.time.LocalDate;

public record ItemDto(
        Long id,
        String title,
        String description,
        LocalDate date,
        MoneyDto compensation,
        String image,
        CategoryDto category,
        PlaceDto place,
        ContactInfoDto contact
) {
}
