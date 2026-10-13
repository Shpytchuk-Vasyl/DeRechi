package org.shpytchuk.ingest.source.telegram;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.SourceSettings;

record FolderChat(String title, @Nullable String username, SourceSettings settings) {
}
