package org.shpytchuk.telegramingest.exception;


public class StageUnavailableException extends RuntimeException {

    public StageUnavailableException(String message) {
        super(message);
    }
}
