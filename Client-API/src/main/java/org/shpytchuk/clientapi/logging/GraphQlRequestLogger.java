package org.shpytchuk.clientapi.logging;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GraphQlRequestLogger implements WebGraphQlInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GraphQlRequestLogger.class);

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        if (!log.isWarnEnabled()) {
            return chain.next(request);
        }
        long start = System.nanoTime();
        return chain.next(request)
                .doOnNext(response -> logResponse(request, response, millisSince(start)))
                .doOnError(failure -> log.error("GraphQL {} -> failed in {} ms",
                        describe(request.getDocument(), request.getOperationName()), millisSince(start), failure));
    }

    private static void logResponse(WebGraphQlRequest request, WebGraphQlResponse response, long millis) {
        String operation = describe(request.getDocument(), request.getOperationName());
        List<ResponseError> errors = response.getErrors();
        if (errors.isEmpty()) {
            log.info("GraphQL {} -> OK in {} ms", operation, millis);
        } else {
            log.warn("GraphQL {} -> {} in {} ms", operation, describe(errors), millis);
        }
    }

    static String describe(String document, String operationName) {
        try {
            Document parsed = Parser.parse(document);
            OperationDefinition operation = parsed.getDefinitionsOfType(OperationDefinition.class).stream()
                    .filter(definition -> operationName == null || operationName.equals(definition.getName()))
                    .findFirst()
                    .orElse(null);
            if (operation == null) {
                return "unknown operation " + operationName;
            }
            String fields = operation.getSelectionSet().getSelectionsOfType(Field.class).stream()
                    .map(Field::getName)
                    .collect(Collectors.joining(", ", "[", "]"));
            String name = operation.getName() == null ? "" : operation.getName() + " ";
            return operation.getOperation().name().toLowerCase() + " " + name + fields;
        } catch (RuntimeException unparseable) {
            return "unparseable document";
        }
    }

    // Messages are left out: they may echo input (the confirmReturn token in NOT_FOUND, values in validation errors)
    private static String describe(List<ResponseError> errors) {
        return errors.stream()
                .map(error -> error.getErrorType() + " at " + error.getPath())
                .collect(Collectors.joining("; "));
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
