package org.shpytchuk.ingest.publish;

import org.shpytchuk.ingest.api.ai.model.ItemKind;

public record SiteItem(ItemKind kind, long id, String url) {
}
