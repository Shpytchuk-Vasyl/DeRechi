package org.shpytchuk.ingest.exception;

public class ClientApiUnavailableException extends StageUnavailableException {

    public ClientApiUnavailableException(String message) {
        super(message);
    }
}
