package org.shpytchuk.clientapi.specification;

import org.shpytchuk.clientapi.entity.Thing;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.NearInput;
import org.shpytchuk.clientapi.util.GeoPoints;
import org.springframework.data.jpa.domain.Specification;

public final class ThingSpecifications {

    private ThingSpecifications() {
    }

    public static <T extends Thing> Specification<T> byFilter(ItemFilterInput filter) {
        SpecificationBuilder<T> builder = new SpecificationBuilder<T>()
                .likeAny(filter.search(), "title", "description", "place.name")
                .equal("category.id", filter.categoryId())
                .greaterThanOrEqualTo("date", filter.dateFrom())
                .lessThanOrEqualTo("date", filter.dateTo());

        NearInput near = filter.near();
        if (near != null)
            builder.within("place.coordinate", GeoPoints.point(near.lat(), near.lon()), near.radiusMeters());

        return builder.build();
    }
}
