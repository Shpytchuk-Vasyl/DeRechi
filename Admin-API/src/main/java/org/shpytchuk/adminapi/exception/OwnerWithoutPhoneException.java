package org.shpytchuk.adminapi.exception;

import lombok.Getter;

@Getter
public class OwnerWithoutPhoneException extends RuntimeException {

    private final Long lostItemId;

    public OwnerWithoutPhoneException(Long lostItemId) {
        super("The owner of lost item %d left no phone".formatted(lostItemId));
        this.lostItemId = lostItemId;
    }
}
