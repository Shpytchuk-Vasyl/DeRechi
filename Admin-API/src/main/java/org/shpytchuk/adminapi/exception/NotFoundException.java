package org.shpytchuk.adminapi.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super("%s.id: %s. NOT_FOUND".formatted(entity, id));
    }
}
