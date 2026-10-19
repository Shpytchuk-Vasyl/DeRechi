package org.shpytchuk.ingest.extraction;

import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record OwnerName(@Nullable String ownerName, @Nullable String title) {

    public boolean found() {
        return StringUtils.hasText(ownerName) && StringUtils.hasText(title);
    }
}
