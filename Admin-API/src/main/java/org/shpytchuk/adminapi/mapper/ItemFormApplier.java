package org.shpytchuk.adminapi.mapper;

import lombok.AllArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.repository.detail.PlaceRepository;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
@AllArgsConstructor
public class ItemFormApplier {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final CountriesProperties countries;

    public void apply(ItemForm form, Thing item, ContactInfo info) {
        ThingCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new NotFoundException("entity.category", form.getCategoryId()));
        String countryCode = countryCode(form);
        Place place = placeRepository.save(toPlace(form, countryCode));

        info.setPhone(form.getPhone());
        info.setEmail(StringUtils.hasText(form.getEmail()) ? form.getEmail() : null);
        info.setSocialMedias(toArray(form.getSocialMedias()));
        info.setNickName(form.getNickName());
        info.setProfileUrl(form.getProfileUrl());

        item.setTitle(form.getTitle());
        item.setDescription(StringUtils.hasText(form.getDescription()) ? form.getDescription() : null);
        item.setImage(StringUtils.hasText(form.getImage()) ? form.getImage() : null);
        item.setDate(form.getDate());
        item.setCompensation(form.getRewardKind() == null
                ? form.getCompensation()
                : form.getRewardKind().compensation(form.getCompensation()));
        item.setCurrency(currency(form, countryCode));
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
    }

    private String countryCode(ItemForm form) {
        String code = upper(form.getCountryCode());
        if (!countries.supports(code)) {
            throw new IllegalArgumentException("Unsupported country: " + code);
        }
        return code;
    }

    private String currency(ItemForm form, String countryCode) {
        String currency = form.getCurrency() == null
                ? countries.currencyOf(countryCode)
                : upper(form.getCurrency());
        if (!countries.currencies().contains(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return currency;
    }

    private static Place toPlace(ItemForm form, String countryCode) {
        Place place = new Place();
        place.setGooglePlaceId(form.getPlaceId());
        place.setName(form.getPlaceName());
        place.setCountryCode(countryCode);
        Point coordinate = GEOMETRY_FACTORY.createPoint(new Coordinate(form.getLon(), form.getLat()));
        place.setCoordinate(coordinate);
        return place;
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return CollectionUtils.isEmpty(socialMedias)
                ? null
                : socialMedias.toArray(SocialMediaEnum[]::new);
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
