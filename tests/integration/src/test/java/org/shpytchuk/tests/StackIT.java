package org.shpytchuk.tests;

import org.junit.jupiter.api.BeforeAll;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Black-box tests against the stack from tests/docker-compose.yml: requests go to Client-API over
 * HTTP, effects are read from PostgreSQL and Mailpit. Skipped when the stack's variables are not set.
 */
abstract class StackIT {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final Duration EVENTUALLY = Duration.ofSeconds(30);

    static String clientApi;
    static String mailpit;
    private static final Map<String, String> CATEGORY_IDS = new HashMap<>();

    enum Kind {
        LOST("Lost"), FOUND("Found");

        final String type;

        Kind(String type) {
            this.type = type;
        }

        String table() {
            return name().toLowerCase() + "_item";
        }

        String claimTable() {
            return table() + "_claim";
        }
    }

    record Contact(String phone, String email) {
    }

    record Item(Kind kind, long id, String title, Contact author) {
    }

    record Claim(long id, boolean repeated) {
    }

    @BeforeAll
    static void stack() {
        clientApi = System.getenv("CLIENT_API_URL");
        mailpit = System.getenv("MAILPIT_URL");
        assumeTrue(clientApi != null && mailpit != null, "run through tests/run.sh");
    }

    // --- Client-API -------------------------------------------------------------------------

    static JsonNode graphql(String query, Map<String, Object> variables) {
        JsonNode response = graphqlResponse(query, variables);
        assertThat(response.has("errors")).as("GraphQL errors: %s", response.path("errors")).isFalse();
        return response.path("data");
    }

    static JsonNode graphqlResponse(String query, Map<String, Object> variables) {
        HttpResponse<String> response = post(clientApi + "/graphql",
                JSON.writeValueAsString(Map.of("query", query, "variables", variables)), Map.of());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    static Item createItem(Kind kind, String title, String categoryKey, double lat, double lon) {
        Contact author = contact("author");
        Map<String, Object> input = new HashMap<>(Map.of(
                "title", title,
                "date", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(),
                "categoryId", categoryId(categoryKey),
                "place", Map.of("id", "it-" + tag(), "name", "Integration test " + tag(),
                        "lat", lat, "lon", lon, "countryCode", "UA"),
                "contact", Map.of("phone", author.phone(), "email", author.email())));
        if (kind == Kind.FOUND) {
            input.put("image", "http://localhost/it.jpg");
        }
        String mutation = "mutation($input: ItemInput!) { create%sItem(input: $input) { id } }".formatted(kind.type);
        long id = graphql(mutation, Map.of("input", input)).path("create" + kind.type + "Item").path("id").asLong();
        return new Item(kind, id, title, author);
    }

    static Item createItem(Kind kind) {
        return createItem(kind, "Гаманець " + tag(), "WALLET", randomLat(), randomLon());
    }

    static Claim claim(Item item, Contact claimant) {
        String mutation = """
                mutation($id: ID!, $contact: ContactInfoInput!) {
                  claim%sItem(id: $id, contact: $contact) { id repeated }
                }""".formatted(item.kind().type);
        JsonNode claim = graphql(mutation, Map.of("id", item.id(),
                "contact", Map.of("phone", claimant.phone(), "email", claimant.email())))
                .path("claim" + item.kind().type + "Item");
        return new Claim(claim.path("id").asLong(), claim.path("repeated").asBoolean());
    }

    static String categoryId(String key) {
        if (CATEGORY_IDS.isEmpty()) {
            for (JsonNode category : graphql("{ categories { id key } }", Map.of()).path("categories")) {
                CATEGORY_IDS.put(category.path("key").asString(), category.path("id").asString());
            }
        }
        return CATEGORY_IDS.get(key);
    }

    static HttpResponse<String> post(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("POST " + url, e);
        }
    }

    static String hmacSha256Base64(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // --- PostgreSQL ------------------------------------------------------------------------

    static Connection db() throws SQLException {
        return DriverManager.getConnection(System.getenv("POSTGRES_URL"),
                System.getenv("POSTGRES_USER"), System.getenv("POSTGRES_PASSWORD"));
    }

    static long count(String sql, Object... args) {
        Object value = single(sql, args);
        return value == null ? 0 : ((Number) value).longValue();
    }

    static Object single(String sql, Object... args) {
        try (Connection connection = db(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getObject(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    static void update(String sql, Object... args) {
        try (Connection connection = db(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            assertThat(statement.executeUpdate()).as(sql).isEqualTo(1);
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    // --- Mailpit ---------------------------------------------------------------------------

    record Mail(String id, String subject) {

        String text() {
            return get(mailpit + "/api/v1/message/" + id).path("Text").asString();
        }
    }

    static List<Mail> mailsTo(String address) {
        String query = URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8);
        List<Mail> mails = new ArrayList<>();
        for (JsonNode message : get(mailpit + "/api/v1/search?query=" + query).path("messages")) {
            mails.add(new Mail(message.path("ID").asString(), message.path("Subject").asString()));
        }
        return mails;
    }

    static Mail awaitOneMailTo(String address) {
        await().atMost(EVENTUALLY).pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(mailsTo(address)).hasSize(1));
        return mailsTo(address).getFirst();
    }

    private static JsonNode get(String url) {
        try {
            HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(url).isEqualTo(200);
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("GET " + url, e);
        }
    }

    // --- test data -------------------------------------------------------------------------

    static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A Ukrainian number, so the e-mails come in Ukrainian. */
    static Contact contact(String role) {
        String phone = "+38067" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        return new Contact(phone, role + "-" + tag() + "@it.derechi.test");
    }

    /** Somewhere in western Ukraine, far enough apart that tests rarely see each other's items. */
    static double randomLat() {
        return 48.5 + ThreadLocalRandom.current().nextDouble(2.5);
    }

    static double randomLon() {
        return 23.5 + ThreadLocalRandom.current().nextDouble(10);
    }

    static String digits(String text) {
        return text.replaceAll("\\D", "");
    }
}
