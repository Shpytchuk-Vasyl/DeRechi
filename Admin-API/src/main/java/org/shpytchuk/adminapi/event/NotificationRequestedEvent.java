package org.shpytchuk.adminapi.event;

import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;

@EventType("NOTIFICATION")
public record NotificationRequestedEvent(
        String subject,
        String message,
        String phone,
        String email,
        SocialMediaEnum[] socialMedias,
        String deduplicationKey) {
}
