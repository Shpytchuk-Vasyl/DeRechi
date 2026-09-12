package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.PlaceRepository;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReferenceService {

    private final ThingCategoryRepository categoryRepository;
    private final PlaceRepository placeRepository;

    public List<CategoryDto> categories() {
        return categoryRepository.findAll().stream().map(ItemMapper::toDto).toList();
    }

    public List<PlaceDto> places(String name) {
        List<Place> places = name == null || name.isBlank()
                ? placeRepository.findAll()
                : placeRepository.findByNameContainingIgnoreCaseOrderByName(name);
        return places.stream().map(ItemMapper::toDto).toList();
    }
}
