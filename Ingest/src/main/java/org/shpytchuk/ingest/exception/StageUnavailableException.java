package org.shpytchuk.ingest.exception;


public class StageUnavailableException extends RuntimeException {

    public StageUnavailableException(String message) {
        super(message);
    }
}
