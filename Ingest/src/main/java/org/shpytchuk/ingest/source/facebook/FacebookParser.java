package org.shpytchuk.ingest.source.facebook;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.exception.IngestException;
import org.shpytchuk.ingest.exception.StageUnavailableException;
import org.shpytchuk.ingest.source.PhotoRef;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FacebookParser {

    private static final Pattern JSON_SCRIPT =
            Pattern.compile("<script type=\"application/json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern SCRIPT_SRC =
            Pattern.compile("<script[^>]+src=\"(https://static\\.xx\\.fbcdn\\.net/rsrc\\.php/[^\"]+)\"");
    private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");
    private static final Pattern TITLE_NOISE = Pattern.compile("^\\(\\d+\\)\\s*|\\s*\\|\\s*Facebook$");
    private static final Pattern USER_ID = Pattern.compile("\"USER_ID\":\"(\\d+)\"");
    private static final Pattern FB_DTSG = Pattern.compile("\"DTSGInitialData\",\\[],\\{\"token\":\"([^\"]+)\"");
    private static final Pattern LSD = Pattern.compile("\"LSD\",\\[],\\{\"token\":\"([^\"]+)\"");
    private static final Pattern CHECKPOINTED = Pattern.compile("\"is_checkpointed\":true");
    private static final String ANONYMOUS = "0";
    private static final String ERROR_PREFIX = "for (;;);";
    private static final String FEED_PAGE_INFO_LABEL = "group_feed$page_info";
    private static final String DISCUSSION = "DISCUSSION";

    private final JsonMapper json;

    FacebookParser(JsonMapper json) {
        this.json = json;
    }

    GroupPage groupPage(String html) {
        String userId = find(USER_ID, html).filter(id -> !ANONYMOUS.equals(id))
                .orElseThrow(() -> new StageUnavailableException(
                        "Facebook session is not logged in, refresh FACEBOOK_COOKIES"));
        String lsd = find(LSD, html).orElseThrow(() -> unreadable(html, "Facebook group page has no LSD token"));
        Feed feed = new Feed();
        JsonNode variables = null;
        Matcher scripts = JSON_SCRIPT.matcher(html);
        while (scripts.find()) {
            JsonNode blob = readTree(scripts.group(1));
            if (blob == null) {
                continue;
            }
            feed.collect(blob);
            if (variables == null) {
                variables = feedVariables(blob);
            }
        }
        if (variables == null) {
            throw unreadable(html, "Facebook group page \"" + title(html) + "\" has no feed query");
        }
        return new GroupPage(userId, find(FB_DTSG, html).orElse(null), lsd, title(html), variables, feed.page(),
                scriptUrls(html));
    }

    FeedPage feedPage(String body) {
        if (body.startsWith(ERROR_PREFIX)) {
            JsonNode error = readTree(body.substring(ERROR_PREFIX.length()));
            String summary = error == null ? "unreadable error" : error.path("errorSummary").asString("unknown error");
            throw new IngestException("Facebook feed answered with an error: " + summary);
        }
        Feed feed = new Feed();
        List<String> errors = new ArrayList<>();
        body.lines().filter(line -> !line.isBlank()).forEach(line -> {
            JsonNode payload = readTree(line);
            if (payload == null) {
                throw new IngestException("Facebook feed answered with something that is not JSON");
            }
            feed.collect(payload);
            payload.path("errors").values().forEach(error -> errors.add(error.path("message").asString("")));
        });
        if (feed.stories.isEmpty() && !feed.ended && feed.cursor == null) {
            throw new IngestException("Facebook feed answered without stories: "
                    + (errors.isEmpty() ? "no data" : errors.getFirst()));
        }
        return feed.page();
    }

    static Optional<String> docId(String script, String query) {
        Matcher found = Pattern.compile(Pattern.quote(query)
                + "_facebookRelayOperation\",\\[],\\(function\\([^)]*\\)\\{\\w+\\.exports=\"(\\d+)\"").matcher(script);
        return found.find() ? Optional.of(found.group(1)) : Optional.empty();
    }

    private @Nullable JsonNode readTree(String text) {
        try {
            return json.readTree(text);
        } catch (JacksonException notJson) {
            return null;
        }
    }

    private static @Nullable JsonNode feedVariables(JsonNode node) {
        if (node.isObject()) {
            JsonNode variables = node.path("variables");
            if (DISCUSSION.equals(variables.path("feedType").asString("")) && variables.has("groupID")) {
                return variables;
            }
        }
        for (JsonNode child : node.values()) {
            JsonNode found = feedVariables(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static RuntimeException unreadable(String html, String problem) {
        return CHECKPOINTED.matcher(html).find() ? new FacebookCheckpointException() : new IngestException(problem);
    }

    private static Optional<String> find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String title(String html) {
        return find(TITLE, html).map(HtmlUtils::htmlUnescape).map(title -> TITLE_NOISE.matcher(title).replaceAll(""))
                .orElse("Facebook");
    }

    private static List<URI> scriptUrls(String html) {
        List<URI> urls = new ArrayList<>();
        Matcher matcher = SCRIPT_SRC.matcher(html);
        while (matcher.find()) {
            urls.add(URI.create(HtmlUtils.htmlUnescape(matcher.group(1))));
        }
        return urls;
    }

    private static final class Feed {

        private final Map<String, FacebookStory> stories = new LinkedHashMap<>();
        private @Nullable String cursor;
        private boolean ended;

        void collect(JsonNode node) {
            if (node.isObject()) {
                if (node.has("post_id") && node.has("creation_time") && node.has("comet_sections")) {
                    stories.putIfAbsent(node.path("post_id").asString(), story(node));
                    return;
                }
                if (node.path("group_feed").has("page_info")) {
                    pageInfo(node.path("group_feed").path("page_info"));
                }
                if (node.path("label").asString("").endsWith(FEED_PAGE_INFO_LABEL)) {
                    pageInfo(node.path("data").path("page_info"));
                }
            }
            node.values().forEach(this::collect);
        }

        FeedPage page() {
            return new FeedPage(List.copyOf(stories.values()), cursor);
        }

        private void pageInfo(JsonNode pageInfo) {
            if (pageInfo.path("has_next_page").asBoolean(false)) {
                cursor = pageInfo.path("end_cursor").asString(null);
            } else {
                ended = true;
            }
        }

        private static FacebookStory story(JsonNode node) {
            String text = node.path("comet_sections").path("content").path("story").path("message").path("text")
                    .asString("");
            return new FacebookStory(node.path("post_id").asString(),
                    Instant.ofEpochSecond(node.path("creation_time").asLong()), text, photos(node.path("attachments")));
        }

        private static List<PhotoRef> photos(JsonNode attachments) {
            Map<String, PhotoRef> photos = new LinkedHashMap<>();
            images(attachments, "viewer_image", photos);
            if (photos.isEmpty()) {
                images(attachments, "photo_image", photos);
            }
            return List.copyOf(photos.values());
        }

        private static void images(JsonNode node, String key, Map<String, PhotoRef> photos) {
            JsonNode image = node.path(key);
            if (image.has("uri")) {
                String uri = image.path("uri").asString();
                photos.putIfAbsent(uri, new PhotoRef(uri, image.path("width").asInt(0), image.path("height").asInt(0)));
            }
            node.values().forEach(child -> images(child, key, photos));
        }
    }
}
