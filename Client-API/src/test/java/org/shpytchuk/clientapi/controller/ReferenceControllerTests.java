package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;

class ReferenceControllerTests extends AbstractGraphQlTests {

    @Test
    void returnsTheCategoriesSeededByTheMigrations() {
        tester.document("{ categories { id key } }")
                .execute()
                .path("categories[*].key").entityList(String.class)
                .contains("DOCUMENTS", "WALLET", "ELECTRONICS", "JEWELRY", "ANIMALS", "OTHER",
                        "KEYS", "BAGS", "CLOTHING");
    }

    @Test
    void exposesTheGeneratedIdentifiers() {
        tester.document("{ categories { id key } }")
                .execute()
                .path("categories[0].id").entity(String.class).satisfies(id ->
                        org.assertj.core.api.Assertions.assertThat(id).isNotBlank());
    }
}
