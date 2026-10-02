package org.shpytchuk.clientapi.repository;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.clientapi.support.Fixtures.place;

class PlaceRepositoryTests extends AbstractRepositoryTests {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void roundTripsAGeographyPointWithoutLosingPrecision() {
        placeRepository.save(place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        entityManager.flush();
        entityManager.clear();

        Point coordinate = placeRepository.findById("ChIJrynok").orElseThrow().getCoordinate();

        assertThat(coordinate.getY()).isEqualTo(49.8419);
        assertThat(coordinate.getX()).isEqualTo(24.0315);
    }

    @Test
    void keepsSrid4326OnTheWayBack() {
        placeRepository.save(place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        entityManager.flush();
        entityManager.clear();

        Point coordinate = placeRepository.findById("ChIJrynok").orElseThrow().getCoordinate();

        assertThat(coordinate.getSRID()).isEqualTo(4326);
    }

    @Test
    void storesSouthernAndWesternHemispheresAsNegativeValues() {
        placeRepository.save(place("ChIJsydney", "Sydney", -33.8688, 151.2093));
        placeRepository.save(place("ChIJlima", "Lima", -12.0464, -77.0428));
        entityManager.flush();
        entityManager.clear();

        Point sydney = placeRepository.findById("ChIJsydney").orElseThrow().getCoordinate();
        Point lima = placeRepository.findById("ChIJlima").orElseThrow().getCoordinate();

        assertThat(sydney.getY()).isEqualTo(-33.8688);
        assertThat(sydney.getX()).isEqualTo(151.2093);
        assertThat(lima.getY()).isEqualTo(-12.0464);
        assertThat(lima.getX()).isEqualTo(-77.0428);
    }
}
