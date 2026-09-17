package org.shpytchuk.adminapi.repository;

import jakarta.persistence.criteria.Predicate;
import org.shpytchuk.adminapi.entity.Thing;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ThingSpecifications {

    private ThingSpecifications() {
    }

    public static <T extends Thing> Specification<T> matching(ItemFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.q() != null) {
                String pattern = "%" + filter.q().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("place").get("name")), pattern)));
            }
            if (filter.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("date"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("date"), filter.to()));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
