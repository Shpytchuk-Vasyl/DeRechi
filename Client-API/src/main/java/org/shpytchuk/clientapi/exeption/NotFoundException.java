package org.shpytchuk.clientapi.exeption;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String entity, Long id) {
        super("%s.id: %d. NOT_FOUND".formatted(entity, id));
    }
}
