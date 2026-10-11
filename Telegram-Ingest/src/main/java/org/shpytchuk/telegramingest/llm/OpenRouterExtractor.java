package org.shpytchuk.telegramingest.llm;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.telegramingest.config.IngestProperties;
import org.shpytchuk.telegramingest.config.OpenRouterProperties;
import org.shpytchuk.telegramingest.exception.BudgetExceededException;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.shpytchuk.telegramingest.exception.StageUnavailableException;
import org.shpytchuk.telegramingest.http.HttpCalls;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class OpenRouterExtractor implements Extractor {

    private static final String OPERATION = "OpenRouter completion";
    private static final String JPEG_DATA_URL = "data:image/jpeg;base64,";
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^```\\w*\\s*(.*?)\\s*```$");

    private final RestClient restClient;
    private final RetryTemplate retry;
    private final OpenRouterProperties properties;
    private final PromptBuilder prompts;
    private final DailyCallCounter counter;
    private final ObjectReader answerReader;

    public OpenRouterExtractor(RestClient.Builder builder, OpenRouterProperties properties, IngestProperties ingest,
                               PromptBuilder prompts, DailyCallCounter counter, JsonMapper jsonMapper) {
        this.restClient = builder
                .baseUrl(properties.url())
                .defaultHeaders(headers -> {
                    headers.setBearerAuth(properties.apiKey());
                    headers.set("HTTP-Referer", ingest.siteUrl());
                    headers.set("X-Title", "DeRechi");
                })
                .build();
        this.retry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(), HttpCalls::transientFailure);
        this.properties = properties;
        this.prompts = prompts;
        this.counter = counter;
        this.answerReader = jsonMapper.rebuild()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build()
                .readerFor(Extraction.class);
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        if (!properties.configured()) {
            throw new StageUnavailableException("OpenRouter API key is not configured");
        }
        if (!counter.tryAcquire(properties.dailyLimit())) {
            throw new BudgetExceededException("The daily limit of " + properties.dailyLimit() + " OpenRouter calls is used up");
        }
        ObjectNode body = body(request);
        long started = System.nanoTime();
        try {
            ExtractionResult result;
            try {
                result = complete(body, started);
            } catch (InvalidAnswer e) {
                log.warn("OpenRouter answer is not a valid extraction ({}), asking again", e.getMessage());
                result = complete(body, started);
            }
            log.debug("OpenRouter answered with {} in {} ms", result.model(), result.duration().toMillis());
            return result;
        } catch (InvalidAnswer e) {
            throw new IngestException("OpenRouter gave no valid extraction twice: " + e.getMessage());
        } catch (RestClientException e) {
            throw new IngestException("OpenRouter failed: " + HttpCalls.describe(e));
        }
    }

    private ObjectNode body(ExtractionRequest request) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("model", properties.model());
        if (!properties.fallbackModel().isEmpty() && !properties.fallbackModel().equals(properties.model())) {
            root.putArray("models").add(properties.model()).add(properties.fallbackModel());
        }
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompts.systemPrompt());
        ArrayNode content = messages.addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "text").put("text", prompts.userText(request));
        for (byte[] photo : request.photos()) {
            content.addObject().put("type", "image_url")
                    .putObject("image_url").put("url", JPEG_DATA_URL + Base64.getEncoder().encodeToString(photo));
        }
        root.put("temperature", 0);
        root.putObject("response_format").put("type", "json_schema").putObject("json_schema")
                .put("name", "notice").put("strict", true).set("schema", prompts.schema(request.categories()));
        root.putObject("provider").put("require_parameters", true);
        return root;
    }

    private ExtractionResult complete(ObjectNode body, long started) {
        Completion completion = HttpCalls.execute(retry, OPERATION, () -> restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Completion.class));
        String content = completion == null ? null : completion.content();
        if (!StringUtils.hasText(content)) {
            throw new InvalidAnswer("no message content");
        }
        String json = withoutCodeFence(content.strip());
        Extraction extraction;
        try {
            extraction = answerReader.readValue(json);
        } catch (JacksonException e) {
            // Jackson's message quotes the answer; the type and the path are enough to see what broke
            throw new InvalidAnswer(e.getClass().getSimpleName() + " at " + e.getPathReference());
        }
        if (extraction == null) {
            throw new InvalidAnswer("null content");
        }
        String model = StringUtils.hasText(completion.model()) ? completion.model() : properties.model();
        BigDecimal cost = completion.usage() == null ? null : completion.usage().cost();
        return new ExtractionResult(extraction, json, model, cost, Duration.ofNanos(System.nanoTime() - started));
    }

    private static String withoutCodeFence(String content) {
        Matcher fenced = CODE_FENCE.matcher(content);
        return fenced.matches() ? fenced.group(1) : content;
    }

    private record Completion(@Nullable String model, @Nullable List<Choice> choices, @Nullable Usage usage) {

        @Nullable String content() {
            if (choices == null || choices.isEmpty() || choices.getFirst().message() == null) {
                return null;
            }
            return choices.getFirst().message().content();
        }
    }

    private record Choice(@Nullable Message message) {
    }

    private record Message(@Nullable String content) {
    }

    private record Usage(@Nullable BigDecimal cost) {
    }

    private static final class InvalidAnswer extends RuntimeException {

        InvalidAnswer(String message) {
            super(message, null, false, false);
        }
    }
}
