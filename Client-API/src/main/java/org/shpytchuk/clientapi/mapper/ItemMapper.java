package org.shpytchuk.clientapi.mapper;

import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.ContactInfoDto;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.ContactInfo;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.entity.Thing;
import org.shpytchuk.clientapi.entity.ThingCategory;
import org.locationtech.jts.geom.Point;

import java.util.Arrays;
import java.util.List;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemDto toDto(Thing item) {
        return new ItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getDate(),
                item.getCompensation(),
                item.getImage(),
                toDto(item.getCategory()),
                toDto(item.getPlace()),
                toDto(item.getInfo())
        );
    }

    public static CategoryDto toDto(ThingCategory category) {
        return new CategoryDto(category.getId(), category.getKey());
    }

    public static PlaceDto toDto(Place place) {
        Point coordinate = place.getCoordinate();
        return new PlaceDto(
                place.getGooglePlaceId(),
                place.getName(),
                coordinate == null ? null : coordinate.getY(),
                coordinate == null ? null : coordinate.getX()
        );
    }

    public static ContactInfoDto toDto(ContactInfo info) {
        List<org.shpytchuk.clientapi.entity.ContactInfo.SocialMediaEnum> socialMedias =
                info.getSocialMedias() == null ? List.of() : Arrays.asList(info.getSocialMedias());
        return new ContactInfoDto(info.getId(), info.getPhone(), info.getEmail(), socialMedias);
    }
}
