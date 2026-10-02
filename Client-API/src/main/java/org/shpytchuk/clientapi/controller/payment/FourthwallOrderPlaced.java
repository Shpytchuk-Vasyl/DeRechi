package org.shpytchuk.clientapi.controller.payment;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public record FourthwallOrderPlaced(
        String orderId,
        String friendlyId,
        String status,
        String email,
        String username,
        BigDecimal amount,
        String currency,
        boolean testMode,
        Instant placedAt,
        Set<String> itemIds
) {

    public static final String TYPE = "ORDER_PLACED";

    public static Optional<String> type(JsonNode root) {
        String type = text(root, "type");
        return type.isEmpty() ? Optional.empty() : Optional.of(type);
    }

    public static Optional<FourthwallOrderPlaced> parse(JsonNode root) {
        if (!TYPE.equals(text(root, "type"))) {
            return Optional.empty();
        }
        JsonNode data = root.path("data");
        JsonNode total = data.path("amounts").path("total");
        String orderId = firstText(data, "id", "orderId");
        String status = text(data, "status");
        String currency = text(total, "currency");
        BigDecimal amount = decimal(total.path("value"));
        Instant placedAt = instant(data.path("createdAt")).or(() -> instant(root.path("createdAt"))).orElse(null);
        if (orderId.isEmpty() || status.isEmpty() || currency.isEmpty() || amount == null || placedAt == null) {
            return Optional.empty();
        }
        return Optional.of(new FourthwallOrderPlaced(
                orderId,
                text(data, "friendlyId"),
                status,
                text(data, "email"),
                text(data, "username"),
                amount,
                currency,
                root.path("testMode").asBoolean(false),
                placedAt,
                itemIds(data)));
    }

    private static Set<String> itemIds(JsonNode data) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode offer : data.path("offers")) {
            add(ids, text(offer, "id"));
            add(ids, text(offer.path("variant"), "id"));
        }
        for (JsonNode variant : data.path("variants")) {
            add(ids, text(variant, "id"));
        }
        return Set.copyOf(ids);
    }

    private static void add(Set<String> ids, String id) {
        if (!id.isEmpty()) {
            ids.add(id);
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue().strip() : "";
    }

    private static BigDecimal decimal(JsonNode value) {
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.stringValue().strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Optional<Instant> instant(JsonNode value) {
        if (!value.isString()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.stringValue().strip()));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "FourthwallOrderPlaced[orderId=" + orderId + ", status=" + status + ", amount=" + amount + " " + currency
                + ", items=" + itemIds + (testMode ? ", test" : "") + "]";
    }
}
