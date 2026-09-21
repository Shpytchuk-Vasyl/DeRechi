package org.shpytchuk.adminapi.form;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;

public enum NotifyChannel {

    ALL,
    EMAIL,
    PHONE,
    TELEGRAM,
    VIBER,
    WHATSAPP;

    public @Nullable SocialMediaEnum social() {
        return switch (this) {
            case ALL, EMAIL, PHONE -> null;
            default -> SocialMediaEnum.valueOf(name());
        };
    }
}
