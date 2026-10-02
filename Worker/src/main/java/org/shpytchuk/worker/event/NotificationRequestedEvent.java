package org.shpytchuk.worker.event;

import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        @Nullable String phone,
        @Nullable String email,
        @NonNull SocialMediaEnum[] socialMedias,
        String deduplicationKey) {
}
