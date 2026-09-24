package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.PlaceRepository;
import org.shpytchuk.clientapi.specification.SpecificationBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PlaceService {

    private static final Sort BY_NAME = Sort.by("name", "googlePlaceId");

    private final PlaceRepository repository;

    public Window<PlaceDto> findAll(String name, ScrollSubrange subrange) {
        PageRequest pageRequest = OffsetPagination.pageRequest(subrange, BY_NAME);
        Specification<Place> spec = new SpecificationBuilder<Place>()
                .like("name", name)
                .build();
        return OffsetPagination.window(repository.findAll(spec, pageRequest), ItemMapper::toDto);
    }
}
