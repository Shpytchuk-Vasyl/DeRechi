package org.shpytchuk.ingest.phone;

public record FoundPhone(String raw, int start, int end, String e164) {
}
