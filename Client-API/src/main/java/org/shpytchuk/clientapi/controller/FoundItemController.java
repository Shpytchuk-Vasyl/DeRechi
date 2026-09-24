package org.shpytchuk.clientapi.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.ItemInput;
import org.shpytchuk.clientapi.dto.ItemSort;
import org.shpytchuk.clientapi.service.FoundItemService;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.query.ScrollSubrange;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

@Controller
@Validated
@AllArgsConstructor
public class FoundItemController {

    private final FoundItemService service;

    @QueryMapping
    public ItemDto foundItem(@Argument Long id) {
        return service.findById(id).orElse(null);
    }

    @QueryMapping
    public Window<ItemDto> foundItems(@Argument @Valid ItemFilterInput filter, @Argument ItemSort sort, ScrollSubrange subrange) {
        return service.findAll(filter, sort, subrange);
    }

    @MutationMapping
    public ItemDto createFoundItem(@Argument @Valid ItemInput input) {
        return service.create(input);
    }

//    @MutationMapping
//    public ItemDto updateFoundItem(@Argument Long id, @Argument @Valid ItemInput input) {
//        return service.update(id, input);
//    }
//
//    @MutationMapping
//    public boolean deleteFoundItem(@Argument Long id) {
//        return service.delete(id);
//    }
}
