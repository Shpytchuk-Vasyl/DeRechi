package org.shpytchuk.ingest.image;

public record RedactedImage(byte[] jpeg, RedactionMode mode) {
}
