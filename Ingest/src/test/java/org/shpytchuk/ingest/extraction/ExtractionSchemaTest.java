package org.shpytchuk.ingest.extraction;

import org.junit.jupiter.api.Test;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode template() throws IOException {
        return JSON.readTree(new ClassPathResource(PromptBuilder.SCHEMA).getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> names(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.values().forEach(value -> values.add(value.isNull() ? null : value.asString()));
        return values;
    }

    @Test
    void mirrorsEveryComponentOfExtractionAndRequiresThemAll() throws IOException {
        JsonNode schema = template();

        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.path("properties").propertyNames()).containsExactlyElementsOf(names(Extraction.class));
        assertThat(strings(schema.path("required"))).containsExactlyElementsOf(names(Extraction.class));
        assertThat(schema.path("additionalProperties").isBoolean()).isTrue();
        assertThat(schema.path("additionalProperties").booleanValue()).isFalse();
    }

    @Test
    void compensationMirrorsMoneyAndMayBeNull() throws IOException {
        JsonNode compensation = template().path("properties").path("compensation");

        assertThat(strings(compensation.path("type"))).containsExactly("object", "null");
        assertThat(compensation.path("properties").propertyNames()).containsExactlyElementsOf(names(Money.class));
        assertThat(strings(compensation.path("required"))).containsExactlyElementsOf(names(Money.class));
        assertThat(compensation.path("additionalProperties").booleanValue(true)).isFalse();
    }

    @Test
    void everyObjectForbidsAdditionalProperties() throws IOException {
        List<String> open = new ArrayList<>();
        walk(template(), "$", open);

        assertThat(open).isEmpty();
    }

    private static void walk(JsonNode node, String path, List<String> open) {
        if (node.isObject()) {
            boolean isObjectSchema = node.path("type").asString("").equals("object")
                    || strings(node.path("type").isArray() ? node.path("type") : JSON.createArrayNode()).contains("object");
            if (isObjectSchema && node.path("additionalProperties").booleanValue(true)) {
                open.add(path);
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                walk(entry.getValue(), path + "." + entry.getKey(), open);
            }
        } else if (node.isArray()) {
            node.values().forEach(value -> walk(value, path + "[]", open));
        }
    }

    @Test
    void everyOtherFieldIsNullableAndIsNoticeIsAPlainBoolean() throws IOException {
        JsonNode properties = template().path("properties");

        assertThat(properties.path("isNotice").path("type").asString()).isEqualTo("boolean");
        for (String name : names(Extraction.class)) {
            if (!name.equals("isNotice")) {
                assertThat(strings(properties.path(name).path("type"))).as(name).contains("null");
            }
        }
    }

    @Test
    void enumsMatchTheJavaEnums() throws IOException {
        JsonNode properties = template().path("properties");

        List<String> kinds = new ArrayList<>(Arrays.stream(ItemKind.values()).map(Enum::name).toList());
        kinds.add(null);
        assertThat(strings(properties.path("kind").path("enum"))).containsExactlyElementsOf(kinds);
        assertThat(strings(properties.path("socialMedias").path("items").path("enum")))
                .containsExactly("TELEGRAM", "VIBER", "WHATSAPP")
                .isSubsetOf(Arrays.stream(SocialMedia.values()).map(Enum::name).toList());
        assertThat(strings(properties.path("category").path("enum")))
                .containsExactly("DOCUMENTS", "WALLET", "ELECTRONICS", "JEWELRY", "ANIMALS", "KEYS", "BAGS", "OTHER", null);
    }

    @Test
    void theCategoryEnumIsTakenFromTheRequest() throws IOException {
        PromptBuilder prompts = new PromptBuilder(JSON);

        JsonNode schema = prompts.schema(List.of("DOCUMENTS", "BICYCLES"));

        assertThat(strings(schema.path("properties").path("category").path("enum")))
                .containsExactly("DOCUMENTS", "BICYCLES", null);
        assertThat(strings(prompts.schema(List.of("KEYS")).path("properties").path("category").path("enum")))
                .containsExactly("KEYS", null);
        assertThat(schema.path("properties").propertyNames()).containsExactlyElementsOf(names(Extraction.class));
    }
}
