package org.shpytchuk.telegramingest.model;

public enum Outcome {
    NO_PHONE,
    DUPLICATE,
    NOT_NOTICE,
    REJECTED,
    INCOMPLETE,
    TOO_OLD,
    NO_PHOTO,
    NO_PLACE,
    UNSUPPORTED_COUNTRY,
    BUDGET,
    /** A stage is not configured or Client-API is unreachable; nothing recorded, the next catch-up retries. */
    UNAVAILABLE,
    PREVIEWED,
    PUBLISHED,
    FAILED;

    public String tag() {
        return name().toLowerCase();
    }
}
