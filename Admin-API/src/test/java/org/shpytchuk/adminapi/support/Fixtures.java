package org.shpytchuk.adminapi.support;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.entity.found.FoundItemClaim;
import org.shpytchuk.adminapi.entity.found.FoundItemHistory;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.lost.LostItemClaim;
import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.entity.matching.Claim;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.form.ItemForm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entities and forms for the database-backed tests. */
public final class Fixtures {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private Fixtures() {
    }

    public static Place place(String googlePlaceId, String name, double lat, double lon) {
        Place place = new Place();
        place.setGooglePlaceId(googlePlaceId);
        place.setName(name);
        place.setCountryCode("UA");
        place.setCoordinate(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)));
        return place;
    }

    public static ContactInfo contact(String phone, String email, SocialMediaEnum... socialMedias) {
        ContactInfo contact = new ContactInfo();
        contact.setPhone(phone);
        contact.setEmail(email);
        contact.setSocialMedias(socialMedias.length == 0 ? null : socialMedias);
        return contact;
    }

    public static <T extends Thing> T item(T item, String title, ThingCategory category, Place place, ContactInfo info) {
        item.setTitle(title);
        item.setDate(LocalDate.of(2026, 9, 10));
        item.setCurrency("UAH");
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
        if (item instanceof LostItemHistory history) {
            history.setArchivedAt(Instant.parse("2026-10-01T12:00:00Z"));
        } else if (item instanceof FoundItemHistory history) {
            history.setArchivedAt(Instant.parse("2026-10-01T12:00:00Z"));
        }
        return item;
    }

    /** A claim on a live notice or, for a history copy, on an archived one. */
    public static <C extends Claim> C claim(C claim, Thing item, ContactInfo claimant, Instant createdAt) {
        switch (item) {
            case LostItem lost -> ((LostItemClaim) claim).setItem(lost);
            case FoundItem found -> ((FoundItemClaim) claim).setItem(found);
            case LostItemHistory history -> ((LostItemClaim) claim).setArchivedItem(history);
            case FoundItemHistory history -> ((FoundItemClaim) claim).setArchivedItem(history);
            default -> throw new IllegalArgumentException("Unknown item type " + item.getClass());
        }
        claim.setContactInfo(claimant);
        claim.setToken(UUID.randomUUID().toString());
        claim.setCreatedAt(createdAt);
        return claim;
    }

    public static ItemForm form(String title, Long categoryId, String placeId, String countryCode) {
        ItemForm form = new ItemForm();
        form.setTitle(title);
        form.setDescription("  ");
        form.setDate(LocalDate.of(2026, 9, 10));
        form.setCompensation(500);
        form.setCategoryId(categoryId);
        form.setPlaceId(placeId);
        form.setPlaceName("Площа Ринок");
        form.setCountryCode(countryCode);
        form.setLat(49.8419);
        form.setLon(24.0315);
        form.setPhone("+380671234567");
        form.setEmail("finder@example.com");
        form.setSocialMedias(List.of(SocialMediaEnum.TELEGRAM));
        return form;
    }
}
