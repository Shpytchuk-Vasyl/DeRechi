package org.shpytchuk.adminapi.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.Place;
import org.shpytchuk.adminapi.entity.Thing;
import org.shpytchuk.adminapi.entity.ThingCategory;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.*;
import org.shpytchuk.adminapi.view.ItemView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public abstract class AdminItemService<T extends Thing> {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ThingRepository<T> repository;
    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final CountriesProperties countries;
    private final Supplier<T> factory;
    private final String scopeKey;

    protected AdminItemService(ThingRepository<T> repository,
                               ThingCategoryRepository categoryRepository,
                               PlaceRepository placeRepository,
                               ContactInfoRepository contactInfoRepository,
                               CountriesProperties countries,
                               Supplier<T> factory,
                               String scopeKey) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.placeRepository = placeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.countries = countries;
        this.factory = factory;
        this.scopeKey = scopeKey;
    }

    protected abstract void deleteMatches(Long id);

    protected abstract void archiveItem(T item);

    @Transactional(readOnly = true)
    public Page<ItemView> page(Pageable pageable, ItemFilter filter) {
        return repository.findAll(ThingSpecifications.matching(filter), Pages.safe(pageable)).map(ItemMapper::toView);
    }

    @Transactional(readOnly = true)
    public ItemForm form(Long id) {
        return ItemMapper.toForm(require(id));
    }

    @Transactional
    public T create(ItemForm form) {
        T item = factory.get();
        ContactInfo info = new ContactInfo();
        apply(form, item, info);
        contactInfoRepository.save(info);
        return repository.save(item);
    }

    @Transactional
    public T update(Long id, ItemForm form) {
        T item = require(id);
        apply(form, item, item.getInfo());
        return repository.save(item);
    }

    @Transactional
    public void archive(Long id) {
        T item = require(id);
        archiveItem(item);
        deleteMatches(id);
        repository.delete(item);
    }

    @Transactional
    public void delete(Long id) {
        T item = require(id);
        deleteMatches(id);
        repository.delete(item);
    }

    public String scopeKey() {
        return scopeKey;
    }

    private T require(Long id) {
        return repository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("entity." + scopeKey, id));
    }

    private void apply(ItemForm form, T item, ContactInfo info) {
        ThingCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new NotFoundException("entity.category", form.getCategoryId()));
        String countryCode = countryCode(form);
        Place place = placeRepository.save(toPlace(form, countryCode));

        info.setPhone(form.getPhone());
        info.setEmail(form.getEmail());
        info.setSocialMedias(toArray(form.getSocialMedias()));

        item.setTitle(form.getTitle());
        item.setDescription(blankToNull(form.getDescription()));
        item.setImage(blankToNull(form.getImage()));
        item.setDate(form.getDate());
        item.setCompensation(form.getCompensation());
        item.setCurrency(currency(form, countryCode));
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
    }

    private String countryCode(ItemForm form) {
        String code = upper(form.getCountryCode());
        if (!countries.supports(code)) {
            throw new IllegalArgumentException("Непідтримувана країна: " + code);
        }
        return code;
    }

    private String currency(ItemForm form, String countryCode) {
        String currency = form.getCurrency() == null
                ? countries.currencyOf(countryCode)
                : upper(form.getCurrency());
        if (!countries.currencies().contains(currency)) {
            throw new IllegalArgumentException("Непідтримувана валюта: " + currency);
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
        return socialMedias == null || socialMedias.isEmpty()
                ? null
                : socialMedias.toArray(SocialMediaEnum[]::new);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
