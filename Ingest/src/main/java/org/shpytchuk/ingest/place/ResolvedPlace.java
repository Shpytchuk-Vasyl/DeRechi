package org.shpytchuk.ingest.place;

public record ResolvedPlace(String id, String name, double lat, double lon, String countryCode) {
}
