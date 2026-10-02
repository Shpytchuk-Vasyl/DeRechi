package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.ErrorType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceControllerTests extends AbstractGraphQlTests {

    private static final String SEARCH = """
            query Places($name: String, $first: Int, $after: String) {
              places(name: $name, first: $first, after: $after) {
                edges { node { id name lat lon countryCode } }
                pageInfo { endCursor hasNextPage }
              }
            }
            """;

    @Autowired
    private PlaceRepository placeRepository;

    @BeforeEach
    void seedPlaces() {
        placeRepository.saveAll(List.of(
                Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315),
                Fixtures.place("ChIJopera", "Оперний театр", 49.8440, 24.0263),
                Fixtures.place("ChIJvysokyi", "Високий Замок", 49.8483, 24.0395)));
    }

    @Test
    void returnsEveryPlaceSortedByName() {
        tester.document(SEARCH)
                .execute()
                .path("places.edges[*].node.name").entityList(String.class)
                .containsExactly("Високий Замок", "Оперний театр", "Площа Ринок");
    }

    @Test
    void exposesTheGooglePlaceIdAndCoordinates() {
        tester.document(SEARCH)
                .variable("name", "ринок")
                .execute()
                .path("places.edges[0].node.id").entity(String.class).isEqualTo("ChIJrynok")
                .path("places.edges[0].node.lat").entity(Double.class).isEqualTo(49.8419)
                .path("places.edges[0].node.lon").entity(Double.class).isEqualTo(24.0315)
                .path("places.edges[0].node.countryCode").entity(String.class).isEqualTo("UA");
    }

    @Test
    void filtersByACaseInsensitiveNameSubstring() {
        tester.document(SEARCH)
                .variable("name", "ЗАМОК")
                .execute()
                .path("places.edges[*].node.name").entityList(String.class)
                .containsExactly("Високий Замок");
    }

    @Test
    void blankNameDoesNotFilter() {
        tester.document(SEARCH)
                .variable("name", "  ")
                .execute()
                .path("places.edges").entityList(Object.class).hasSize(3);
    }

    @Test
    void pagesThroughTheConnectionWithTheReturnedCursor() {
        String cursor = tester.document(SEARCH)
                .variable("first", 2)
                .execute()
                .path("places.edges[*].node.name").entityList(String.class)
                .containsExactly("Високий Замок", "Оперний театр")
                .path("places.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true)
                .path("places.pageInfo.endCursor").entity(String.class).get();

        tester.document(SEARCH)
                .variable("first", 2)
                .variable("after", cursor)
                .execute()
                .path("places.edges[*].node.name").entityList(String.class)
                .containsExactly("Площа Ринок")
                .path("places.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void rejectsAPageSizeOverTheServiceCap() {
        tester.document(SEARCH)
                .variable("first", 500)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("1..100");
                });
    }

    @Test
    void rejectsANameLongerThanTheLimit() {
        tester.document(SEARCH)
                .variable("name", "а".repeat(101))
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("name");
                });
    }
}
