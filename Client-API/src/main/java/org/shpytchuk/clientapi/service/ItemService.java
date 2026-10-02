package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.ItemInput;
import org.shpytchuk.clientapi.input.MoneyInput;
import org.shpytchuk.clientapi.input.PlaceInput;
import org.shpytchuk.clientapi.dto.ItemSort;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.entity.thing.ThingCategory;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.shpytchuk.clientapi.specification.ThingSpecifications;
import org.shpytchuk.clientapi.util.GeoPoints;
import org.springframework.data.domain.*;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ItemService<T extends Thing> {

    private final ThingRepository<T> repository;
    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final CountriesProperties countries;
    private final Supplier<T> factory;
    private final boolean imageRequired;
    private final String entityName;


    public Optional<ItemDto> findById(Long id) {
        return repository.findWithDetailsById(id).map(ItemMapper::toDto);
    }


    public Window<ItemDto> findAll(ItemFilterInput filter, ItemSort sort, ScrollSubrange subrange) {
        ItemFilterInput effectiveFilter = filter == null ? ItemFilterInput.EMPTY : filter;
        PageRequest pageRequest = OffsetPagination.pageRequest(subrange, toSort(sort));
        Page<T> result = repository.findAll(ThingSpecifications.byFilter(effectiveFilter), pageRequest);
        return OffsetPagination.window(result, ItemMapper::toDto);
    }

    @Transactional
    public ItemDto create(ItemInput input) {
        T item = factory.get();
        ContactInfo info = new ContactInfo();
        apply(input, item, info);
        contactInfoRepository.save(info);
        return ItemMapper.toDto(repository.save(item));
    }

//    @Transactional
//    public ItemDto update(Long id, ItemInput input) {
//        T item = repository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException(entityName, id));
//        apply(input, item, item.getInfo());
//        return ItemMapper.toDto(repository.save(item));
//    }
//
//    @Transactional
//    public boolean delete(Long id) {
//        if (!repository.existsById(id)) {
//            throw new NotFoundException(entityName, id);
//        }
//        repository.deleteById(id);
//        return true;
//    }

    private void apply(ItemInput input, T item, ContactInfo info) {
        if (imageRequired && (input.image() == null || input.image().isBlank())) {
            throw new IllegalArgumentException("Image is required");
        }

        ThingCategory category = categoryRepository.findById(input.categoryId()).orElseThrow(() -> new NotFoundException("Category", input.categoryId()));
        Place place = toPlace(input.place());
        String currency = currencyFor(input.compensation(), place.getCountryCode());
        placeRepository.save(place);

        buildItemFromInput(input, item, info, category, place, currency);
    }

    private static <T extends Thing> void buildItemFromInput(ItemInput input, T item, ContactInfo info, ThingCategory category, Place place, String currency) {
        info.setPhone(input.contact().phone());
        info.setEmail(input.contact().email());
        info.setSocialMedias(toArray(input.contact().socialMedias()));

        item.setTitle(input.title());
        item.setDescription(input.description());
        item.setDate(input.date());
        item.setCompensation(input.compensation() == null ? null : input.compensation().amount());
        item.setCurrency(currency);
        item.setImage(input.image());
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
    }

    private Place toPlace(PlaceInput input) {
        String countryCode = upper(input.countryCode());
        if (!countries.supports(countryCode)) {
            throw new IllegalArgumentException("Unsupported country: " + countryCode);
        }
        Place place = new Place();
        place.setGooglePlaceId(input.id());
        place.setName(input.name());
        place.setCoordinate(GeoPoints.point(input.lat(), input.lon()));
        place.setCountryCode(countryCode);
        return place;
    }

    private String currencyFor(MoneyInput compensation, String countryCode) {
        String currency = compensation == null || compensation.currency() == null
                ? countries.currencyOf(countryCode)
                : upper(compensation.currency());
        if (!countries.currencies().contains(currency)) {
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        }
        return currency;
    }

    private static String upper(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }

    private static Sort toSort(ItemSort sort) {
        return sort.toSort(Sort.Order.desc("id"));
    }
}
