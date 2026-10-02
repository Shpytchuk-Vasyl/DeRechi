package org.shpytchuk.clientapi.controller;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.CountryDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.service.ReferenceService;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Controller
@Validated
@AllArgsConstructor
public class ReferenceController {

    private final ReferenceService service;

    @QueryMapping
    public List<CategoryDto> categories() {
        return service.categories();
    }

    @QueryMapping
    public List<CountryDto> countries() {
        return service.countries();
    }

    @QueryMapping
    public Window<PlaceDto> places(@Argument @Size(max = 100) String name, ScrollSubrange subrange) {
        return service.places(name, subrange);
    }

}
