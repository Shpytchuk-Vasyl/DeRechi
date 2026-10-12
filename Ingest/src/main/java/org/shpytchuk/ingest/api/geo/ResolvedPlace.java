package org.shpytchuk.ingest.api.geo;

public record ResolvedPlace(String id, String name, double lat, double lon, String countryCode) {
}
