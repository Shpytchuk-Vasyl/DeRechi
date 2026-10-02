package org.shpytchuk.clientapi.controller.payment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FourthwallOrderPlacedTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String ORDER_PLACED = """
            {
              "testMode": false,
              "id": "evt-1",
              "webhookId": "wh-1",
              "shopId": "sh-1",
              "type": "ORDER_PLACED",
              "apiVersion": "V1",
              "createdAt": "2026-10-03T10:00:05Z",
              "data": {
                "id": "ord-1",
                "friendlyId": "DERECHI-1001",
                "status": "COMPLETED",
                "email": " payer@example.com ",
                "username": "Jo",
                "amounts": {
                  "subtotal": {"value": 1.00, "currency": "USD"},
                  "total": {"value": 1.08, "currency": "USD"}
                },
                "offers": [
                  {"id": "prod-1", "name": "Author's phone number (lost-42)", "variant": {"id": "var-1", "quantity": 1}}
                ],
                "createdAt": "2026-10-03T10:00:00Z"
              }
            }
            """;

    @Test
    void readsTheOrderAndTheProductAndVariantIds() {
        Optional<FourthwallOrderPlaced> parsed = FourthwallOrderPlaced.parse(JSON.readTree(ORDER_PLACED));

        assertThat(parsed).hasValueSatisfying(order -> {
            assertThat(order.orderId()).isEqualTo("ord-1");
            assertThat(order.friendlyId()).isEqualTo("DERECHI-1001");
            assertThat(order.status()).isEqualTo("COMPLETED");
            assertThat(order.email()).isEqualTo("payer@example.com");
            assertThat(order.username()).isEqualTo("Jo");
            assertThat(order.amount()).isEqualByComparingTo(new BigDecimal("1.08"));
            assertThat(order.currency()).isEqualTo("USD");
            assertThat(order.testMode()).isFalse();
            assertThat(order.placedAt()).isEqualTo(Instant.parse("2026-10-03T10:00:00Z"));
            assertThat(order.itemIds()).containsExactlyInAnyOrder("prod-1", "var-1");
        });
    }

    @Test
    void readsTheVariantsShapeAndFallsBackToTheEnvelopeTimeAndOrderId() {
        String body = """
                {"testMode": true, "type": "ORDER_PLACED", "createdAt": "2026-10-03T10:00:05Z",
                 "data": {"orderId": "ord-2", "status": "CONFIRMED",
                          "amounts": {"total": {"value": "1", "currency": "USD"}},
                          "variants": [{"id": "var-2", "quantity": 1}]}}
                """;

        assertThat(FourthwallOrderPlaced.parse(JSON.readTree(body))).hasValueSatisfying(order -> {
            assertThat(order.orderId()).isEqualTo("ord-2");
            assertThat(order.testMode()).isTrue();
            assertThat(order.placedAt()).isEqualTo(Instant.parse("2026-10-03T10:00:05Z"));
            assertThat(order.amount()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(order.itemIds()).containsExactly("var-2");
            assertThat(order.email()).isEmpty();
        });
    }

    @Test
    void isEmptyForAnotherEventTypeOrAnIncompleteOrder() {
        assertThat(FourthwallOrderPlaced.parse(JSON.readTree("{\"type\":\"DONATION\",\"data\":{\"id\":\"d-1\"}}"))).isEmpty();
        assertThat(FourthwallOrderPlaced.parse(JSON.readTree("{\"type\":\"ORDER_PLACED\",\"data\":{\"id\":\"ord-3\"}}"))).isEmpty();
        assertThat(FourthwallOrderPlaced.parse(JSON.readTree(
                "{\"type\":\"ORDER_PLACED\",\"data\":{\"status\":\"COMPLETED\",\"amounts\":{\"total\":{\"value\":1,\"currency\":\"USD\"}},\"createdAt\":\"2026-10-03T10:00:00Z\"}}")))
                .isEmpty();
    }

    @Test
    void tellsTheEventType() {
        assertThat(FourthwallOrderPlaced.type(JSON.readTree("{\"type\":\"DONATION\"}"))).contains("DONATION");
        assertThat(FourthwallOrderPlaced.type(JSON.readTree("{\"data\":{}}"))).isEmpty();
    }
}
