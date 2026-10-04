package org.shpytchuk.clientapi.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphQlRequestLoggerTest {

    @Test
    void namesTheOperationAndItsTopLevelFields() {
        String document = """
                mutation Claim($id: ID!, $contact: ContactInfoInput!) {
                  claimLostItem(id: $id, contact: $contact) { id repeated }
                }
                """;

        assertThat(GraphQlRequestLogger.describe(document, "Claim")).isEqualTo("mutation Claim [claimLostItem]");
    }

    @Test
    void picksTheRequestedOperationOutOfSeveralAndHandlesAnonymousOnes() {
        String document = "query A { categories { id } } query B { countries { code } lostItems { totalCount } }";

        assertThat(GraphQlRequestLogger.describe(document, "B")).isEqualTo("query B [countries, lostItems]");
        assertThat(GraphQlRequestLogger.describe("{ categories { id } }", null)).isEqualTo("query [categories]");
        assertThat(GraphQlRequestLogger.describe("{ broken", null)).isEqualTo("unparseable document");
    }
}
