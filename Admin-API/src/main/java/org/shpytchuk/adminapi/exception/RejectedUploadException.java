package org.shpytchuk.adminapi.exception;

import lombok.Getter;

@Getter
public class RejectedUploadException extends RuntimeException {

    private final String messageKey;

    public RejectedUploadException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }
}
