package org.shpytchuk.clientapi.specification;

import org.springframework.data.jpa.domain.Specification;

public class SpecificationBuilder<T> {

    private Specification<T> specification = Specification.where((Specification<T>) null);

    protected <V> SpecificationBuilder<T> applyIfPresent(
            V value,
            SpecificationBuilder<T> spec
    ) {
        if (value == null)
            return this;

        return spec;
    }

    public SpecificationBuilder<T> and(
            Specification<T> spec
    ) {
        if (spec != null)
            specification = specification.and(spec);
        return this;
    }

    public <V> SpecificationBuilder<T> equal(
            String field,
            V value
    ) {
        return applyIfPresent(value,
                and((root, query, cb) ->
                        cb.equal(root.get(field), value))
        );
    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> greaterThanOrEqualTo(
            String field,
            V value
    ) {
        return applyIfPresent(value,
                and((root, query, cb) ->
                        cb.greaterThanOrEqualTo(root.get(field), value))
        );

    }

    public <V extends Comparable<? super V>> SpecificationBuilder<T> lessThanOrEqualTo(
            String field,
            V value
    ) {
        return applyIfPresent(value,
                and((root, query, cb) ->
                        cb.lessThanOrEqualTo(root.get(field), value)));
    }

    public <V> SpecificationBuilder<T> equalNested(
            String parent,
            String field,
            V value
    ) {
        return applyIfPresent(value,
                and((root, query, cb) ->
                        cb.equal(
                                root.get(parent).get(field),
                                value
                        )));
    }

    public SpecificationBuilder<T> like(
            String field,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            and((root, query, cb) ->
                    cb.like(
                            cb.lower(root.get(field)),
                            "%" + value.toLowerCase() + "%"
                    ));
        }

        return this;
    }

    public Specification<T> build() {
        return specification;
    }
}

