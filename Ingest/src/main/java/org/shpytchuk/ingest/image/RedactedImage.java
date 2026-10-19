package org.shpytchuk.ingest.image;

import java.util.List;

public record RedactedImage(byte[] jpeg, RedactionMode mode, List<String> text) {

    public RedactedImage(byte[] jpeg, RedactionMode mode) {
        this(jpeg, mode, List.of());
    }
}
