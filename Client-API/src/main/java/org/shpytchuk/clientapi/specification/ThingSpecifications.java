package org.shpytchuk.clientapi.specification;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.shpytchuk.clientapi.config.FullTextFunctionContributor;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.NearInput;
import org.shpytchuk.clientapi.util.GeoPoints;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ThingSpecifications {

    private ThingSpecifications() {
    }

    public static <T extends Thing> Specification<T> byFilter(ItemFilterInput filter) {
        SpecificationBuilder<T> builder = new SpecificationBuilder<T>()
                .or(SpecificationBuilder.containsAny(filter.search(), "title", "description", "place.name"),
                        matchesWords(filter.search()))
                .equal("category.id", filter.categoryId())
                .greaterThanOrEqualTo("date", filter.dateFrom())
                .lessThanOrEqualTo("date", filter.dateTo());

        NearInput near = filter.near();
        if (near != null)
            builder.within("place.coordinate", GeoPoints.point(near.lat(), near.lon()), near.radiusMeters());

        return builder.build();
    }

    private static <T extends Thing> Specification<T> matchesWords(String search) {
        if (!StringUtils.hasText(search))
            return null;

        return (root, query, cb) -> cb.isTrue(cb.function(FullTextFunctionContributor.MATCHES_WORDS_FUNCTION, Boolean.class,
                root.get("language"), root.get("title"), root.get("description"),
                ((HibernateCriteriaBuilder) cb).value(search)));
    }
}
