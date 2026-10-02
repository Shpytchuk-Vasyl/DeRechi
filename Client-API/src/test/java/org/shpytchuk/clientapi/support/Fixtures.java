package org.shpytchuk.clientapi.support;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.entity.thing.ThingCategory;

import java.time.LocalDate;

public final class Fixtures {

    public static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private Fixtures() {
    }

    public static Point point(double lat, double lon) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    public static Place place(String googlePlaceId, String name, double lat, double lon) {
        return place(googlePlaceId, name, lat, lon, "UA");
    }

    public static Place place(String googlePlaceId, String name, double lat, double lon, String countryCode) {
        Place place = new Place();
        place.setGooglePlaceId(googlePlaceId);
        place.setName(name);
        place.setCoordinate(point(lat, lon));
        place.setCountryCode(countryCode);
        return place;
    }

    public static ContactInfo contact(SocialMediaEnum... socialMedias) {
        ContactInfo info = new ContactInfo();
        info.setPhone("+380671234567");
        info.setEmail("finder@example.com");
        info.setSocialMedias(socialMedias.length == 0 ? null : socialMedias);
        return info;
    }

    public static <T extends Thing> T item(T item, String title, LocalDate date,
                                    ThingCategory category, Place place, ContactInfo info) {
        item.setTitle(title);
        item.setDate(date);
        item.setCurrency("UAH");
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
        return item;
    }
}
