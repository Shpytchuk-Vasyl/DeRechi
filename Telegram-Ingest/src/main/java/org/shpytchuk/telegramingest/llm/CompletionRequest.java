package org.shpytchuk.telegramingest.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
record CompletionRequest(
        String model,
        @Nullable List<String> models,
        List<Message> messages,
        int temperature,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        Provider provider) {

    sealed interface Message permits SystemMessage, UserMessage {
    }

    record SystemMessage(String role, String content) implements Message {

        SystemMessage(String content) {
            this("system", content);
        }
    }

    record UserMessage(String role, List<Part> content) implements Message {

        UserMessage(List<Part> content) {
            this("user", content);
        }
    }

    sealed interface Part permits TextPart, ImagePart {
    }

    record TextPart(String type, String text) implements Part {

        TextPart(String text) {
            this("text", text);
        }
    }

    record ImagePart(String type, @JsonProperty("image_url") ImageUrl imageUrl) implements Part {

        static ImagePart jpeg(byte[] photo) {
            return new ImagePart("image_url",
                    new ImageUrl("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photo)));
        }
    }

    record ImageUrl(String url) {
    }

    record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {

        static ResponseFormat strictSchema(String name, JsonNode schema) {
            return new ResponseFormat("json_schema", new JsonSchema(name, true, schema));
        }
    }

    record JsonSchema(String name, boolean strict, JsonNode schema) {
    }

    record Provider(@JsonProperty("require_parameters") boolean requireParameters) {
    }
}
