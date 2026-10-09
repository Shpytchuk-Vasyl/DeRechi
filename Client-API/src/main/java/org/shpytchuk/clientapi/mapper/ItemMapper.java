package org.shpytchuk.clientapi.mapper;

import org.locationtech.jts.geom.Point;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.ContactInfoDto;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.MoneyDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.entity.thing.ThingCategory;
import org.shpytchuk.clientapi.util.ContactMasker;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemDto toDto(Thing item) {
        return new ItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getDate(),
                toMoney(item),
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
                coordinate == null ? null : coordinate.getX(),
                place.getCountryCode()
        );
    }

    private static MoneyDto toMoney(Thing item) {
        return item.getCompensation() == null ? null : new MoneyDto(item.getCompensation(), item.getCurrency());
    }

    public static ContactInfoDto toDto(ContactInfo info) {
        return new ContactInfoDto(
                info.getId(),
                ContactMasker.maskPhone(info.getPhone()),
                info.getEmail() == null ? null : ContactMasker.maskEmail(info.getEmail())
        );
    }
}
