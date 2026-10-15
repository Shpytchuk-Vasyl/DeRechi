package org.shpytchuk.adminapi.event;

import org.locationtech.jts.geom.Point;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.thing.Thing;

import java.time.LocalDate;

@EventType("ITEM_CREATED")
public record ItemCreatedEvent(Long id, LocalDate date, Long category, String categoryKey, Double lat, Double lon,
                               String placeName, String title, String description, Integer compensation,
                               String currency, String image, String origin) {

    private static final String ORIGIN = "admin";

    public static ItemCreatedEvent of(Thing item) {
        Place place = item.getPlace();
        Point point = place.getCoordinate();
        return new ItemCreatedEvent(item.getId(), item.getDate(), item.getCategory().getId(),
                item.getCategory().getKey(), point.getY(), point.getX(), place.getName(), item.getTitle(),
                item.getDescription(), item.getCompensation(), item.getCurrency(), item.getImage(), ORIGIN);
    }

    @Override
    public String toString() {
        return "ItemCreatedEvent[id=" + id + ", category=" + category + "]";
    }
}
