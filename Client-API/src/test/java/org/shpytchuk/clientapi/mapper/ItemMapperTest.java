package org.shpytchuk.clientapi.mapper;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.entity.ContactInfo;
import org.shpytchuk.clientapi.entity.LostItem;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.entity.ThingCategory;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ItemMapperTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void mapsAllFields() {
        ItemDto dto = ItemMapper.toDto(item(point(49.8419, 24.0315)));

        assertThat(dto.title()).isEqualTo("Ключі");
        assertThat(dto.description()).isEqualTo("Звʼязка з брелоком");
        assertThat(dto.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dto.compensation()).isEqualTo(500);
        assertThat(dto.image()).isEqualTo("keys.png");
        assertThat(dto.category().key()).isEqualTo("keys");
        assertThat(dto.place().id()).isEqualTo("ChIJplaceId");
        assertThat(dto.place().name()).isEqualTo("Площа Ринок");
        assertThat(dto.contact().id()).isEqualTo(4L);
    }

    @Test
    void masksPhoneAndEmailOfTheContact() {
        ItemDto dto = ItemMapper.toDto(item(point(49.8419, 24.0315)));

        assertThat(dto.contact().phone()).isEqualTo("+38067*****67");
        assertThat(dto.contact().email()).isEqualTo("f*****r@example.com");
    }

    @Test
    void readsLatitudeFromYAndLongitudeFromX() {
        ItemDto dto = ItemMapper.toDto(item(point(49.8419, 24.0315)));

        assertThat(dto.place().lat()).isEqualTo(49.8419);
        assertThat(dto.place().lon()).isEqualTo(24.0315);
    }

    @Test
    void toleratesPlaceWithoutCoordinate() {
        ItemDto dto = ItemMapper.toDto(item(null));

        assertThat(dto.place().lat()).isNull();
        assertThat(dto.place().lon()).isNull();
    }

    private static org.locationtech.jts.geom.Point point(double lat, double lon) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    private static LostItem item(org.locationtech.jts.geom.Point coordinate) {
        ThingCategory category = new ThingCategory();
        category.setId(2L);
        category.setKey("keys");

        Place place = new Place();
        place.setGooglePlaceId("ChIJplaceId");
        place.setName("Площа Ринок");
        place.setCoordinate(coordinate);

        ContactInfo info = new ContactInfo();
        info.setId(4L);
        info.setPhone("+380671234567");
        info.setEmail("finder@example.com");

        LostItem item = new LostItem();
        item.setId(1L);
        item.setTitle("Ключі");
        item.setDescription("Звʼязка з брелоком");
        item.setDate(LocalDate.of(2026, 9, 1));
        item.setCompensation(500);
        item.setImage("keys.png");
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
        return item;
    }
}
