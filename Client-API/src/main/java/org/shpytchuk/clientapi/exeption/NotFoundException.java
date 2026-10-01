package org.shpytchuk.clientapi.exeption;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Long id) {
        super("%s.id: %d. NOT_FOUND".formatted(entity, id));
    }

    public NotFoundException(String entity, String id) {
        super("%s.id: %s. NOT_FOUND".formatted(entity, id));
    }
}
