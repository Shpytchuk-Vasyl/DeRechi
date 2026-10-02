package org.shpytchuk.adminapi.event;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        @Nullable String phone,
        @Nullable String email,
        @NonNull SocialMediaEnum[] socialMedias,
        String deduplicationKey) {
}
