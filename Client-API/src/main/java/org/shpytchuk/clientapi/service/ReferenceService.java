package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.CountryDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.specification.SpecificationBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReferenceService {

    private static final Sort BY_NAME = Sort.by("name", "googlePlaceId");

    private final ThingCategoryRepository categoryRepository;
    private final CountriesProperties countries;
    private final PlaceRepository places;


    public List<CategoryDto> categories() {
        return categoryRepository.findAll().stream().map(ItemMapper::toDto).toList();
    }

    public List<CountryDto> countries() {
        return countries.supported().stream()
                .map(code -> new CountryDto(code, countries.currencyOf(code)))
                .toList();
    }

    public Window<PlaceDto> places(String name, ScrollSubrange subrange) {
        PageRequest pageRequest = OffsetPagination.pageRequest(subrange, BY_NAME);
        Specification<Place> spec = new SpecificationBuilder<Place>()
                .like("name", name)
                .build();
        return OffsetPagination.window(places.findAll(spec, pageRequest), ItemMapper::toDto);
    }

}
