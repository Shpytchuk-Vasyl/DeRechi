package org.shpytchuk.adminapi.mapper;

import org.locationtech.jts.geom.Point;
import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.Place;
import org.shpytchuk.adminapi.entity.Thing;
import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.view.CandidateView;
import org.shpytchuk.adminapi.view.ItemView;

import java.util.Arrays;
import java.util.List;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemView toView(Thing item) {
        Place place = item.getPlace();
        Point coordinate = place.getCoordinate();
        ContactInfo info = item.getInfo();

        return new ItemView(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getDate(),
                item.getCompensation(),
                item.getCurrency(),
                item.getImage(),
                item.getCategory().getKey(),
                place.getName(),
                place.getCountryCode(),
                coordinate == null ? null : coordinate.getY(),
                coordinate == null ? null : coordinate.getX(),
                info.getPhone(),
                info.getEmail(),
                socialMedias(info)
        );
    }

    public static CandidateView toCandidate(SimilarItem similar) {
        return new CandidateView(
                toView(similar.getFoundItem()),
                similar.getMatchOrder(),
                similar.getNotifiedAt(),
                similar.getNotifiedBy());
    }

    public static void copy(Thing from, Thing to) {
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setImage(from.getImage());
        to.setDate(from.getDate());
        to.setCompensation(from.getCompensation());
        to.setCurrency(from.getCurrency());
        to.setInfo(from.getInfo());
        to.setPlace(from.getPlace());
        to.setCategory(from.getCategory());
    }

    public static ItemForm toForm(Thing item) {
        Place place = item.getPlace();
        Point coordinate = place.getCoordinate();
        ContactInfo info = item.getInfo();

        ItemForm form = new ItemForm();
        form.setId(item.getId());
        form.setTitle(item.getTitle());
        form.setDescription(item.getDescription());
        form.setImage(item.getImage());
        form.setDate(item.getDate());
        form.setCompensation(item.getCompensation());
        form.setCurrency(item.getCurrency());
        form.setCategoryId(item.getCategory().getId());
        form.setPlaceId(place.getGooglePlaceId());
        form.setPlaceName(place.getName());
        form.setCountryCode(place.getCountryCode());
        form.setLat(coordinate == null ? null : coordinate.getY());
        form.setLon(coordinate == null ? null : coordinate.getX());
        form.setPhone(info.getPhone());
        form.setEmail(info.getEmail());
        form.setSocialMedias(socialMedias(info));
        return form;
    }

    private static List<ContactInfo.SocialMediaEnum> socialMedias(ContactInfo info) {
        return info.getSocialMedias() == null ? List.of() : Arrays.asList(info.getSocialMedias());
    }
}
