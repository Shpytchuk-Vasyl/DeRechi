package org.shpytchuk.clientapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.FoundItemStatisticsDto;
import org.shpytchuk.clientapi.service.found.FoundItemStatisticsService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@AllArgsConstructor
public class FoundItemStatisticsController {

    private final FoundItemStatisticsService service;

    @QueryMapping
    public FoundItemStatisticsDto stats() {
        return service.stats();
    }

}
