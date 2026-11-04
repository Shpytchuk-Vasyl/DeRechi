package org.shpytchuk.ingest.source.facebook;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.ingest.source.PhotoRef;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ApifyPost(@Nullable String id, @Nullable String legacyId, @Nullable Instant time, @Nullable String text,
                 @Nullable String groupTitle, @Nullable User user, @Nullable List<Attachment> attachments,
                 @Nullable String error) {

    private static final Pattern AUTHOR_IN_STORY_ID = Pattern.compile("^S:_I(\\d+):");

    boolean failed() {
        return error != null && legacyId == null;
    }

    boolean complete() {
        return legacyId != null && time != null;
    }

    List<PhotoRef> photos() {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .filter(Attachment::isPhoto)
                .map(attachment -> attachment.photoImage().toPhotoRef())
                .distinct()
                .toList();
    }

    @Nullable String authorId() {
        if (id == null) {
            return null;
        }
        try {
            Matcher author = AUTHOR_IN_STORY_ID.matcher(new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8));
            return author.find() ? author.group(1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    record User(@Nullable String name) {
    }

    record Attachment(@JsonProperty("__typename") @Nullable String type,
                      @JsonProperty("photo_image") @Nullable Image photoImage) {

        boolean isPhoto() {
            return "Photo".equals(type) && photoImage != null && photoImage.uri() != null;
        }
    }

    record Image(@Nullable String uri, int width, int height) {

        PhotoRef toPhotoRef() {
            return new PhotoRef(uri, width, height);
        }
    }
}
