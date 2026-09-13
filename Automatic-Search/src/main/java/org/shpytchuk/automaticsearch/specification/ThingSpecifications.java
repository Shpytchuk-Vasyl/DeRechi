package org.shpytchuk.automaticsearch.specification;

import jakarta.persistence.criteria.Expression;
import org.hibernate.spatial.predicate.JTSSpatialPredicates;
import org.shpytchuk.automaticsearch.config.FullTextFunctionContributor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.automaticsearch.entity.Thing;
import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.Period;

public final class ThingSpecifications {

    private static final Period PERIOD = Period.ofDays(5);

    private static final double RADIUS_METERS = 50_000;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private ThingSpecifications() {
    }

    public static <T extends Thing> Specification<T> byFilter(ItemCreatedEvent event) {
        LocalDate date = event.getDate();

        return ThingSpecifications.<T>categoryIs(event.getCategory())
                .and(dateBetween(date.minus(PERIOD), date.plus(PERIOD)))
                .and(nearTo(event.getLat(), event.getLon()))
                .and(orderByRelevance(event.getTitle()));
    }

    private static <T extends Thing> Specification<T> categoryIs(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static <T extends Thing> Specification<T> dateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("date"), from, to);
    }

    private static <T extends Thing> Specification<T> orderByRelevance(String title) {
        return (root, query, cb) -> {
            if (query == null || title == null || title.isBlank()) {
                return null;
            }

            Expression<String> document = cb.concat(
                    cb.concat(root.<String>get("title"), " "),
                    cb.coalesce(root.<String>get("description"), ""));

            Expression<Double> rank = cb.function(
                    FullTextFunctionContributor.TS_RANK_SIMPLE, Double.class, document, cb.literal(title));

            query.orderBy(cb.desc(rank), cb.asc(root.get("id")));
            return null;
        };
    }

    private static <T extends Thing> Specification<T> nearTo(double lat, double lon) {
        Point center = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));

        return (root, query, cb) -> JTSSpatialPredicates.distanceWithin(
                cb, root.get("place").<Point>get("coordinate"), center, RADIUS_METERS);
    }
}
