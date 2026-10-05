package org.shpytchuk.tests;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PaymentWebhookIT extends StackIT {

    private static final String WEBHOOK = "/api/client/webhooks/fourthwall";

    @Test
    void aPaidOrderSendsTheAuthorsPhoneToTheClaimant() {
        Item item = createItem(Kind.LOST);
        Contact claimant = contact("claimant");
        Claim claim = claim(item, claimant);
        String variant = "it-var-" + tag();
        update("update lost_item_claim set payment_variant_id = ? where id = ?", variant, claim.id());

        HttpResponse<String> response = sendWebhook(order(variant), System.getenv("FOURTHWALL_WEBHOOK_SECRET"));

        assertThat(response.statusCode()).isEqualTo(200);
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(count(
                "select count(*) from lost_item_claim where id = ? and paid_at is not null and contacts_sent_at is not null",
                claim.id())).isEqualTo(1));
        Mail mail = awaitOneMailTo(claimant.email());
        assertThat(mail.subject()).isEqualTo("DeRechi: номер автора оголошення «%s»".formatted(item.title()));
        assertThat(digits(mail.text())).contains(digits(item.author().phone()));
        JsonNode status = graphql("""
                query($itemId: ID!, $id: ID!) { lostItemClaim(itemId: $itemId, id: $id) { paid contactsSent } }""",
                Map.of("itemId", item.id(), "id", claim.id())).path("lostItemClaim");
        assertThat(status.path("paid").asBoolean()).isTrue();
        assertThat(status.path("contactsSent").asBoolean()).isTrue();
    }

    @Test
    void aWebhookWithAWrongSignatureIsRejected() {
        Item item = createItem(Kind.LOST);
        Claim claim = claim(item, contact("claimant"));
        String variant = "it-var-" + tag();
        update("update lost_item_claim set payment_variant_id = ? where id = ?", variant, claim.id());

        HttpResponse<String> response = sendWebhook(order(variant), "not-the-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(count("select count(*) from lost_item_claim where id = ? and paid_at is null", claim.id()))
                .isEqualTo(1);
    }

    private static String order(String variant) {
        return JSON.writeValueAsString(Map.of(
                "type", "ORDER_PLACED",
                "testMode", true,
                "data", Map.of(
                        "id", "it-ord-" + tag(),
                        "status", "CONFIRMED",
                        "amounts", Map.of("total", Map.of("value", 1, "currency", "USD")),
                        "createdAt", Instant.now().toString(),
                        "offers", java.util.List.of(Map.of("id", "it-prod-" + tag(), "variant", Map.of("id", variant))))));
    }

    private static HttpResponse<String> sendWebhook(String body, String secret) {
        return post(clientApi + WEBHOOK, body, Map.of("X-Fourthwall-Hmac-SHA256", hmacSha256Base64(secret, body)));
    }
}
