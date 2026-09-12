package org.shpytchuk.clientapi.controller;

import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.service.ReferenceService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class ReferenceController {

    private final ReferenceService service;

    public ReferenceController(ReferenceService service) {
        this.service = service;
    }

    @QueryMapping
    public List<CategoryDto> categories() {
        return service.categories();
    }

}
