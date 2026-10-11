package org.shpytchuk.notification.event;

import org.jspecify.annotations.Nullable;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        @Nullable String phone,
        @Nullable String email,
        SocialMediaEnum[] socialMedias,
        @Nullable String deduplicationKey) {

    public enum SocialMediaEnum {
        TELEGRAM,
        VIBER,
        WHATSAPP
    }
}
