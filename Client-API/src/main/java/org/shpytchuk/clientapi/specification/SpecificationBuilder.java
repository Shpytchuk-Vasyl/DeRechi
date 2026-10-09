package org.shpytchuk.clientapi.specification;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.hibernate.spatial.predicate.JTSSpatialPredicates;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SpecificationBuilder<T> {

    private static final char LIKE_ESCAPE = '\\';

    private Specification<T> specification = Specification.unrestricted();

    public SpecificationBuilder<T> and(Specification<T> spec) {
        if (spec != null)
            specification = specification.and(spec);
        return this;
    }

    @SafeVarargs
    public final SpecificationBuilder<T> or(Specification<T>... specs) {
        List<Specification<T>> present = Arrays.stream(specs)
                .filter(Objects::nonNull)
                .toList();
        if (present.isEmpty())
            return this;

        return and(Specification.anyOf(present));
    }

    public <V> SpecificationBuilder<T> equal(String field, V value) {
        if (value == null)
            return this;

        return and((root, query, cb) -> cb.equal(path(root, field), value));
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> greaterThanOrEqualTo(String field, V value) {
        if (value == null)
            return this;

        return and((root, query, cb) -> cb.greaterThanOrEqualTo(path(root, field), value));
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lessThanOrEqualTo(String field, V value) {
        if (value == null)
            return this;

        return and((root, query, cb) -> cb.lessThanOrEqualTo(path(root, field), value));
    }

    public SpecificationBuilder<T> likeAny(String value, String... fields) {
        if (!StringUtils.hasText(value))
            return this;

        String pattern = containsPattern(value);
        return and(Specification.anyOf(Arrays.stream(fields)
                .map(field -> likeSpec(field, pattern))
                .toList()));
    }

    public SpecificationBuilder<T> like(String field, String value) {
        return likeAny(value, field);
    }

    public SpecificationBuilder<T> within(String field, Point center, double meters) {
        if (center == null)
            return this;

        return and((root, query, cb) ->
                JTSSpatialPredicates.distanceWithin(cb, path(root, field), center, meters));
    }

    public Specification<T> build() {
        return specification;
    }

    private Specification<T> likeSpec(String field, String pattern) {
        return (root, query, cb) ->
                cb.like(cb.lower(path(root, field)), pattern, LIKE_ESCAPE);
    }

    private static String containsPattern(String value) {
        String escaped = value.strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @SuppressWarnings("unchecked")
    private static <Y> Path<Y> path(Root<?> root, String field) {
        Path<?> path = root;
        for (String part : field.split("\\."))
            path = path.get(part);
        return (Path<Y>) path;
    }
}
