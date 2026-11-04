package org.shpytchuk.ingest.source.facebook;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.exception.IngestException;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
class ApifyGroupReader implements GroupReader {

    private static final String PROFILE_BY_ID = FacebookClient.ORIGIN + "/profile.php?id=";

    private final ApifyClient client;

    ApifyGroupReader(ApifyClient client) {
        this.client = client;
    }

    @Override
    public GroupFeed read(String group, Instant since) {
        List<ApifyPost> posts = client.groupPosts(group, since);
        posts.stream().filter(ApifyPost::failed).findFirst().ifPresent(failed -> {
            throw new IngestException("Apify could not read Facebook group " + group + ": " + failed.error());
        });
        List<FacebookStory> stories = posts.stream().filter(ApifyPost::complete).map(ApifyGroupReader::story).toList();
        if (stories.size() < posts.size()) {
            log.warn("Apify returned {} posts of Facebook group {} without an id or a time",
                    posts.size() - stories.size(), group);
        }
        return new GroupFeed(name(group, posts), stories);
    }

    private static String name(String group, List<ApifyPost> posts) {
        return posts.stream().map(ApifyPost::groupTitle).filter(StringUtils::hasText).findFirst().orElse(group);
    }

    private static FacebookStory story(ApifyPost post) {
        String text = post.text() == null ? "" : post.text();
        return new FacebookStory(post.legacyId(), post.time(), text, post.photos(), author(post));
    }

    private static @Nullable FacebookActor author(ApifyPost post) {
        String name = post.user() == null || post.user().name() == null ? "" : post.user().name().strip();
        String id = post.authorId();
        return name.isEmpty() || id == null ? null : new FacebookActor(name, PROFILE_BY_ID + id);
    }
}
