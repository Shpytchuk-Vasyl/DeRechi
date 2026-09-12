package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.mapper.ItemMapper;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ReferenceService {

    private final ThingCategoryRepository categoryRepository;

    public List<CategoryDto> categories() {
        return categoryRepository.findAll().stream().map(ItemMapper::toDto).toList();
    }

}
