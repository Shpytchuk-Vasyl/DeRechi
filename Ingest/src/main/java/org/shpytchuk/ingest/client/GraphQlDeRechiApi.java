package org.shpytchuk.ingest.client;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.config.client.ApiProperties;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.RejectedItemException;
import org.shpytchuk.ingest.model.ItemKind;
import org.shpytchuk.ingest.model.Money;
import org.shpytchuk.ingest.model.SocialMedia;
import org.shpytchuk.ingest.place.ResolvedPlace;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphQlDeRechiApi implements DeRechiApi {

    public static final String CATEGORIES_CACHE = "categories";
    public static final String COUNTRIES_CACHE = "countries";

    private static final String DOCUMENT = "client-api";

    private static final Set<ErrorType> REJECTED = Set.of(ErrorType.BAD_REQUEST, ErrorType.NOT_FOUND);

    private final HttpSyncGraphQlClient client;
    private final RetryTemplate readRetry;
    private final RetryTemplate createRetry;

    public GraphQlDeRechiApi(HttpSyncGraphQlClient client, ApiProperties properties) {
        this.client = client;
        this.readRetry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(),
                HttpCalls::transientFailure);
        this.createRetry = HttpCalls.retryTemplate(properties.maxAttempts(), properties.retryDelay(),
                HttpCalls::notDelivered);
    }

    @Override
    @Cacheable(cacheNames = CATEGORIES_CACHE, sync = true)
    public Map<String, Long> categories() {
        return execute("categories", Map.of(), readRetry).field("categories").toEntityList(Category.class).stream()
                .collect(Collectors.toUnmodifiableMap(Category::key, Category::id));
    }

    @Override
    @Cacheable(cacheNames = COUNTRIES_CACHE, sync = true)
    public Set<String> countries() {
        return execute("countries", Map.of(), readRetry).field("countries").toEntityList(Country.class).stream()
                .map(Country::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public long create(NewItem item) {
        String operation = item.kind() == ItemKind.LOST ? "createLostItem" : "createFoundItem";
        Long id = execute(operation, Map.of("input", ItemInput.of(item)), createRetry)
                .field(operation + ".id").toEntity(Long.class);
        return Objects.requireNonNull(id, operation + " returned no id");
    }

    private ClientGraphQlResponse execute(String field, Map<String, Object> variables, RetryTemplate retry) {
        ClientGraphQlResponse response;
        try {
            response = HttpCalls.execute(retry, field, () -> client.documentName(DOCUMENT)
                    .operationName(StringUtils.capitalize(field))
                    .variables(variables)
                    .executeSync());
        } catch (RuntimeException e) {
            throw new IngestException(field + " failed: " + HttpCalls.describe(e));
        }
        List<ResponseError> errors = response.getErrors();
        if (errors.isEmpty()) {
            return response;
        }
        if (errors.stream().allMatch(error -> REJECTED.contains(error.getErrorType()))) {
            throw new RejectedItemException(field + " rejected: "
                    + errors.stream().map(ResponseError::getMessage).collect(Collectors.joining("; ")));
        }
        throw new IngestException(field + " failed with GraphQL errors "
                + errors.stream().map(error -> error.getErrorType().toString()).distinct().toList());
    }

    private record Category(long id, String key) {
    }

    private record Country(String code) {
    }

    private record ItemInput(String title, @Nullable String description, LocalDate date, @Nullable Money compensation,
                             @Nullable String image, String categoryId, ResolvedPlace place, Contact contact) {

        static ItemInput of(NewItem item) {
            return new ItemInput(item.title(), item.description(), item.date(), item.compensation(), item.imageKey(),
                    Long.toString(item.categoryId()), item.place(), Contact.of(item));
        }
    }

    private record Contact(@Nullable String phone, List<SocialMedia> socialMedias, @Nullable String nickName,
                           @Nullable String profileUrl) {

        static Contact of(NewItem item) {
            return new Contact(item.phone(), item.socialMedias(), item.nickName(), item.profileUrl());
        }
    }
}
