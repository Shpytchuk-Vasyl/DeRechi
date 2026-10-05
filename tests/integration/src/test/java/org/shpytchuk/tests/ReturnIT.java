package org.shpytchuk.tests;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReturnIT extends StackIT {

    private static final String CONFIRM = "mutation($token: String!) { confirmReturn(token: $token) }";

    @Test
    void aConfirmedReturnArchivesTheNotice() {
        Item item = createItem(Kind.LOST);
        Claim claim = claim(item, contact("claimant"));
        String token = (String) single("select token from lost_item_claim where id = ?", claim.id());

        boolean confirmed = graphql(CONFIRM, Map.of("token", token)).path("confirmReturn").asBoolean();

        assertThat(confirmed).isTrue();
        await().atMost(EVENTUALLY).untilAsserted(() -> {
            assertThat(count("select count(*) from lost_item where id = ?", item.id())).isZero();
            assertThat(count("""
                    select count(*) from lost_item_history h
                    join lost_item_claim c on c.archived_item_id = h.id
                    where c.id = ? and h.title = ?""", claim.id(), item.title())).isEqualTo(1);
        });
        JsonNode gone = graphql("query($id: ID!) { lostItem(id: $id) { id } }", Map.of("id", item.id()));
        assertThat(gone.path("lostItem").isNull()).isTrue();
    }

    @Test
    void anUnknownTokenIsNotFound() {
        JsonNode response = graphqlResponse(CONFIRM, Map.of("token", UUID.randomUUID().toString()));

        assertThat(response.path("errors").path(0).path("extensions").path("classification").asString())
                .isEqualTo("NOT_FOUND");
    }
}
