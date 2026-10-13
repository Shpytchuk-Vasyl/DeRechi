package org.shpytchuk.ingest.source.facebook;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.List;

record GroupPage(String userId, @Nullable String fbDtsg, String lsd, String name, JsonNode feedVariables,
                 FeedPage feed, List<URI> scripts) {

    static final String ANONYMOUS = "0";

    boolean loggedIn() {
        return !ANONYMOUS.equals(userId);
    }

    String groupId() {
        return feedVariables.path("groupID").asString();
    }
}
