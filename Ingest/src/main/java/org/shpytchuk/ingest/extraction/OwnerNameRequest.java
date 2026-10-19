package org.shpytchuk.ingest.extraction;

import java.util.List;

public record OwnerNameRequest(String title, List<String> photoText) {
}
