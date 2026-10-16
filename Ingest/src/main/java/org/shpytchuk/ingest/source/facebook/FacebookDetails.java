package org.shpytchuk.ingest.source.facebook;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.SourceDetails;

public record FacebookDetails(@Nullable FacebookActor author) implements SourceDetails {
}
