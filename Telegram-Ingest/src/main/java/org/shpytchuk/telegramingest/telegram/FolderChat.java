package org.shpytchuk.telegramingest.telegram;

import org.jspecify.annotations.Nullable;

record FolderChat(String title, @Nullable String username, ChatSettings settings) {
}
