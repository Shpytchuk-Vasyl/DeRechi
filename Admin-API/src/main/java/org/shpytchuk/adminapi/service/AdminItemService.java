package org.shpytchuk.adminapi.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.Place;
import org.shpytchuk.adminapi.entity.Thing;
import org.shpytchuk.adminapi.entity.ThingCategory;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.ThingRepository;
import org.shpytchuk.adminapi.view.ItemView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

public abstract class AdminItemService<T extends Thing> {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final ThingRepository<T> repository;
    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final Supplier<T> factory;
    private final String entityName;

    protected AdminItemService(ThingRepository<T> repository,
                               ThingCategoryRepository categoryRepository,
                               PlaceRepository placeRepository,
                               ContactInfoRepository contactInfoRepository,
                               Supplier<T> factory,
                               String entityName) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.placeRepository = placeRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.factory = factory;
        this.entityName = entityName;
    }

    protected abstract void deleteMatches(Long id);

    @Transactional(readOnly = true)
    public Page<ItemView> page(int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Order.desc("id")));
        return repository.findAll(pageRequest).map(ItemMapper::toView);
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
    public void delete(Long id) {
        T item = require(id);
        deleteMatches(id);
        repository.delete(item);
    }

    public String entityName() {
        return entityName;
    }

    private T require(Long id) {
        return repository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException(entityName, id));
    }

    private void apply(ItemForm form, T item, ContactInfo info) {
        ThingCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Категорію", form.getCategoryId()));
        Place place = placeRepository.save(toPlace(form));

        info.setPhone(form.getPhone());
        info.setEmail(form.getEmail());
        info.setSocialMedias(toArray(form.getSocialMedias()));

        item.setTitle(form.getTitle());
        item.setDescription(blankToNull(form.getDescription()));
        item.setImage(blankToNull(form.getImage()));
        item.setDate(form.getDate());
        item.setCompensation(form.getCompensation());
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
    }

    private static Place toPlace(ItemForm form) {
        Place place = new Place();
        place.setGooglePlaceId(form.getPlaceId());
        place.setName(form.getPlaceName());
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
}
