package org.shpytchuk.ingest.channel;

import org.jspecify.annotations.Nullable;

import java.util.List;


public record Preview(String sourceTitle, @Nullable String sourceLink, String outcome,
                      @Nullable ChannelPost post, @Nullable String extractionJson, List<byte[]> photos) {
}
