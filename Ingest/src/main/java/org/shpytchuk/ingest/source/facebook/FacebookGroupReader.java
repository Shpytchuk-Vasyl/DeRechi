package org.shpytchuk.ingest.source.facebook;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.exception.IngestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
class FacebookGroupReader {

    static final String FEED_QUERY = "GroupsCometFeedRegularStoriesPaginationQuery";

    private static final int PAGE_SIZE = 3;
    private static final String RELAY_FLAG_PREFIX = "__relay_internal__pv__";

    private final FacebookClient client;
    private final FacebookParser parser;
    private final JsonMapper json;
    private final int maxPages;
    private final Runnable pause;
    private volatile @Nullable String docId;

    FacebookGroupReader(FacebookClient client, JsonMapper json, int maxPages, Runnable pause) {
        this.client = client;
        this.parser = new FacebookParser(json);
        this.json = json;
        this.maxPages = maxPages;
        this.pause = pause;
    }

    GroupFeed read(String group, Instant since) {
        URI url = URI.create(FacebookClient.ORIGIN + "/groups/" + group + "/?sorting_setting=CHRONOLOGICAL");
        GroupPage page = parser.groupPage(client.page(url));
        List<FacebookStory> stories = new ArrayList<>(page.feed().stories());
        String cursor = page.feed().nextCursor();
        for (int pages = 1; pages < maxPages && cursor != null && !reached(stories, since); pages++) {
            pause.run();
            FeedPage next = nextPage(page, url, cursor);
            stories.addAll(next.stories());
            cursor = next.nextCursor();
        }
        return new GroupFeed(page.name(), stories);
    }

    private FeedPage nextPage(GroupPage page, URI referer, String cursor) {
        boolean cached = docId != null;
        try {
            return query(page, referer, cursor);
        } catch (IngestException e) {
            if (!cached) {
                throw e;
            }
            log.warn("Facebook feed query failed, looking up its doc_id again: {}", e.getMessage());
            docId = null;
            return query(page, referer, cursor);
        }
    }

    private FeedPage query(GroupPage page, URI referer, String cursor) {
        return parser.feedPage(client.graphQl(FEED_QUERY, form(page, docId(page), cursor), page.lsd(), referer));
    }

    private String docId(GroupPage page) {
        String known = docId;
        if (known != null) {
            return known;
        }
        for (URI script : page.scripts()) {
            String found = FacebookParser.docId(client.script(script), FEED_QUERY).orElse(null);
            if (found != null) {
                log.info("Facebook {} doc_id is {}", FEED_QUERY, found);
                docId = found;
                return found;
            }
        }
        throw new IngestException("Facebook " + FEED_QUERY + " doc_id not found in " + page.scripts().size() + " scripts");
    }

    private Map<String, String> form(GroupPage page, String docId, String cursor) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("av", page.userId());
        form.put("__user", page.userId());
        form.put("__a", "1");
        if (page.fbDtsg() != null) {
            form.put("fb_dtsg", page.fbDtsg());
            form.put("jazoest", jazoest(page.fbDtsg()));
        }
        form.put("lsd", page.lsd());
        form.put("fb_api_caller_class", "RelayModern");
        form.put("fb_api_req_friendly_name", FEED_QUERY);
        form.put("server_timestamps", "true");
        form.put("doc_id", docId);
        form.put("variables", variables(page, cursor));
        return form;
    }

    private String variables(GroupPage page, String cursor) {
        ObjectNode variables = json.createObjectNode()
                .put("count", PAGE_SIZE)
                .put("cursor", cursor)
                .put("feedLocation", "GROUP")
                .put("feedType", "DISCUSSION")
                .put("feedbackSource", 0)
                .putNull("focusCommentID")
                .put("privacySelectorRenderLocation", "COMET_STREAM")
                .put("renderLocation", "group")
                .put("scale", 1)
                .put("sortingSetting", "CHRONOLOGICAL")
                .put("stream_initial_count", 1)
                .put("useDefaultActor", false)
                .put("id", page.groupId());
        for (Map.Entry<String, JsonNode> flag : page.feedVariables().properties()) {
            if (flag.getKey().startsWith(RELAY_FLAG_PREFIX)) {
                variables.set(flag.getKey(), flag.getValue());
            }
        }
        return json.writeValueAsString(variables);
    }

    private static boolean reached(List<FacebookStory> stories, Instant since) {
        return !stories.isEmpty() && stories.getLast().createdAt().isBefore(since);
    }

    static String jazoest(String fbDtsg) {
        return "2" + fbDtsg.chars().sum();
    }

    record GroupFeed(String name, List<FacebookStory> stories) {
    }
}
