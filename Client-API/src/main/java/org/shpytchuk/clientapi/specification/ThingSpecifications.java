package org.shpytchuk.clientapi.specification;

import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.entity.Thing;
import org.springframework.data.jpa.domain.Specification;

public final class ThingSpecifications {

    public static <T extends Thing> Specification<T> byFilter(
            ItemFilterInput filter
    ) {
        return new SpecificationBuilder<T>()
                .like("title", filter.search())
                .equalNested("category", "id", filter.categoryId())
                .equalNested("place", "googlePlaceId", filter.placeId())
                .greaterThanOrEqualTo("date", filter.dateFrom())
                .lessThanOrEqualTo("date", filter.dateTo())
                .build();
    }
}
