package org.shpytchuk.ingest.image;

/** What was blurred on a photo; also the {@code mode} tag of {@code derechi.ingest.image.redacted}. */
public enum RedactionMode {
    NONE,
    PHONES,
    DOCUMENT,
    FULL
}
