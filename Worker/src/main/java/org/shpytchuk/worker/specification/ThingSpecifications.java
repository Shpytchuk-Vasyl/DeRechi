package org.shpytchuk.worker.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.hibernate.spatial.predicate.JTSSpatialPredicates;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.SearchLanguage;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;

public final class ThingSpecifications {

    private static final Period PERIOD = Period.ofDays(3);

    private static final double RADIUS_METERS = 20_000;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private ThingSpecifications() {
    }

    public static <T extends Thing> Specification<T> byFilter(ItemCreatedEvent event, String text,
                                                              SearchLanguage language) {
        LocalDate date = event.getDate();

        return ThingSpecifications.<T>categoryIs(event.getCategory())
                .and(dateBetween(date.minus(PERIOD), date.plus(PERIOD)))
                .and(nearTo(event.getLat(), event.getLon()))
                .and(relevantTo(text, language));
    }

    private static <T extends Thing> Specification<T> categoryIs(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static <T extends Thing> Specification<T> dateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("date"), from, to);
    }

    public static Expression<Double> matchRank(Root<? extends Thing> root, CriteriaBuilder cb, String text,
                                               SearchLanguage language) {
        return cb.function(language.matchRankFunction(), Double.class, document(root, cb), cb.literal(text));
    }

    private static <T extends Thing> Specification<T> relevantTo(String text, SearchLanguage language) {
        return (root, query, cb) -> StringUtils.hasText(text)
                ? cb.isTrue(cb.function(language.sharesWordsFunction(), Boolean.class, document(root, cb),
                        cb.literal(text)))
                : cb.disjunction();
    }

    private static Expression<String> document(Root<? extends Thing> root, CriteriaBuilder cb) {
        return cb.concat(cb.concat(root.get("title"), " "), cb.coalesce(root.get("description"), ""));
    }

    private static <T extends Thing> Specification<T> nearTo(double lat, double lon) {
        Point center = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));

        return (root, query, cb) -> JTSSpatialPredicates.distanceWithin(
                cb, root.get("place").<Point>get("coordinate"), center, RADIUS_METERS);
    }
}
