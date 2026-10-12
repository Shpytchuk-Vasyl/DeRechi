package org.shpytchuk.ingest.phone;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public record MaskedText(String text, Map<String, String> phones) {

    public @Nullable String phone(@Nullable String ref) {
        if (ref == null) {
            return null;
        }
        return phones.get(ref.replace("[", "").replace("]", "").trim());
    }
}
