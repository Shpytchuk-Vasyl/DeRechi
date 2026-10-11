package org.shpytchuk.telegramingest.image;

public record RedactedImage(byte[] jpeg, RedactionMode mode) {
}
