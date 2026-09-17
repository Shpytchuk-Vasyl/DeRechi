package org.shpytchuk.adminapi.exception;

import lombok.Getter;

/**
 * Назва сутності зберігається ключем, а не текстом: повідомлення для користувача
 * збирає {@code GlobalExceptionHandler} уже його мовою.
 */
@Getter
public class NotFoundException extends RuntimeException {

    private final String entityKey;
    private final Object id;

    public NotFoundException(String entityKey, Object id) {
        super("%s.id: %s. NOT_FOUND".formatted(entityKey, id));
        this.entityKey = entityKey;
        this.id = id;
    }
}
