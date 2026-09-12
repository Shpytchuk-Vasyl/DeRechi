package org.shpytchuk.clientapi.config;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return switch (ex) {
            case NotFoundException e -> error(ErrorType.NOT_FOUND, e.getMessage(), env);
            case ConstraintViolationException e -> error(ErrorType.BAD_REQUEST, describe(e), env);
            case IllegalArgumentException e -> error(ErrorType.BAD_REQUEST, e.getMessage(), env);
            default -> null;
        };
    }

    private static GraphQLError error(ErrorType type, String message, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(message)
                .build();
    }

    private static String describe(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(v -> shortPath(v) + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
    }

    private static String shortPath(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
