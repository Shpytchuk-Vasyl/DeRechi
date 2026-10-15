package org.shpytchuk.ingest.model;

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
    /** A stage is not configured or unreachable; nothing recorded, the queue retries it up to 3 times. */
    UNAVAILABLE,
    PREVIEWED,
    PUBLISHED,
    FAILED;

    public String tag() {
        return name().toLowerCase();
    }
}
