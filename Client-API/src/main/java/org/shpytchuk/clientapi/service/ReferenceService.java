package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.CountryDto;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReferenceService {

    private final ThingCategoryRepository categoryRepository;
    private final CountriesProperties countries;

    public List<CategoryDto> categories() {
        return categoryRepository.findAll().stream().map(ItemMapper::toDto).toList();
    }

    public List<CountryDto> countries() {
        return countries.supported().stream()
                .map(code -> new CountryDto(code, countries.currencyOf(code)))
                .toList();
    }

}
