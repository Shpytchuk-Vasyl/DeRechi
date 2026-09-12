package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.ItemInput;
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
import org.springframework.data.domain.*;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ItemService<T extends Thing> {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

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
        int size = size(subrange);

        PageRequest pageRequest = PageRequest.of((int) (offset(subrange) / size), size, toSort(sort));
        Page<T> result = repository.findAll(ThingSpecifications.byFilter(effectiveFilter), pageRequest);

        List<ItemDto> content = result.getContent().stream().map(ItemMapper::toDto).toList();
        return Window.from(content, OffsetScrollPosition.positionFunction(pageRequest.getOffset()), result.hasNext());
    }

    @Transactional
    public ItemDto create(ItemInput input) {
        T item = factory.get();
        ContactInfo info = new ContactInfo();
        apply(input, item, info);
        contactInfoRepository.save(info);
        return ItemMapper.toDto(repository.save(item));
    }

    @Transactional
    public ItemDto update(Long id, ItemInput input) {
        T item = repository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException(entityName, id));
        apply(input, item, item.getInfo());
        return ItemMapper.toDto(repository.save(item));
    }

    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException(entityName, id);
        }
        repository.deleteById(id);
        return true;
    }

    private void apply(ItemInput input, T item, ContactInfo info) {
        if (imageRequired && (input.image() == null || input.image().isBlank())) {
            throw new IllegalArgumentException("Для знайденої речі фото обовʼязкове");
        }

        ThingCategory category = categoryRepository.findById(input.categoryId()).orElseThrow(() -> new NotFoundException("Категорію", input.categoryId()));
        Place place = placeRepository.findById(input.placeId()).orElseThrow(() -> new NotFoundException("Місце", input.placeId()));

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

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }

    private static int size(ScrollSubrange subrange) {
        int size = subrange.count().orElse(DEFAULT_SIZE);
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("first має бути в межах 1.." + MAX_SIZE);
        }
        return size;
    }

    private static long offset(ScrollSubrange subrange) {
        ScrollPosition position = subrange.position().orElse(null);
        if (position == null || position.isInitial()) {
            return 0;
        }
        if (!(position instanceof OffsetScrollPosition offsetPosition)) {
            throw new IllegalArgumentException("Непідтримуваний курсор");
        }
        return offsetPosition.getOffset() + 1;
    }

    private static Sort toSort(ItemSort sort) {
        return sort.toSort(Sort.Order.desc("id"));
    }
}
