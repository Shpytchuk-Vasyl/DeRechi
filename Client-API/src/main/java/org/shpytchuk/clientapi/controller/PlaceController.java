package org.shpytchuk.clientapi.controller;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.service.PlaceService;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
@AllArgsConstructor
public class PlaceController {

    private final PlaceService service;

    @QueryMapping
    public Window<PlaceDto> places(@Argument @Size(max = 100) String name, ScrollSubrange subrange) {
        return service.findAll(name, subrange);
    }
}
