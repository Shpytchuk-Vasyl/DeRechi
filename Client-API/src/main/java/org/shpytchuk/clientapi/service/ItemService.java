package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.ItemInput;
import org.shpytchuk.clientapi.input.PlaceInput;
import org.shpytchuk.clientapi.dto.ItemSort;
import org.shpytchuk.clientapi.entity.ContactInfo;
import org.shpytchuk.clientapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.entity.Thing;
import org.shpytchuk.clientapi.entity.ThingCategory;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.PlaceRepository;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.shpytchuk.clientapi.repository.ThingRepository;
import org.shpytchuk.clientapi.specification.ThingSpecifications;
import org.shpytchuk.clientapi.util.GeoPoints;
import org.springframework.data.domain.*;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ItemService<T extends Thing> {

    private final ThingRepository<T> repository;
    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;
    private final ContactInfoRepository contactInfoRepository;
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
        Place place = placeRepository.save(toPlace(input.place()));

        buildItemFromInput(input, item, info, category, place);
    }

    private static <T extends Thing> void buildItemFromInput(ItemInput input, T item, ContactInfo info, ThingCategory category, Place place) {
        info.setPhone(input.contact().phone());
        info.setEmail(input.contact().email());
        info.setSocialMedias(toArray(input.contact().socialMedias()));

        item.setTitle(input.title());
        item.setDescription(input.description());
        item.setDate(input.date());
        item.setCompensation(input.compensation());
        item.setImage(input.image());
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
    }

    private static Place toPlace(PlaceInput input) {
        Place place = new Place();
        place.setGooglePlaceId(input.id());
        place.setName(input.name());
        place.setCoordinate(GeoPoints.point(input.lat(), input.lon()));
        return place;
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }

    private static Sort toSort(ItemSort sort) {
        return sort.toSort(Sort.Order.desc("id"));
    }
}
