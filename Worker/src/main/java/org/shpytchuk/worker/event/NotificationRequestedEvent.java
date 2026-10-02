package org.shpytchuk.worker.event;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.worker.entity.ContactInfo.SocialMediaEnum;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        @Nullable String phone,
        @Nullable String email,
        @NonNull SocialMediaEnum[] socialMedias,
        String deduplicationKey) {
}
