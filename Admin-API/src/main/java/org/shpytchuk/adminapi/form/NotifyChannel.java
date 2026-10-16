package org.shpytchuk.adminapi.form;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

public enum NotifyChannel {

    ALL(true, true, null),
    EMAIL(false, true, null),
    PHONE(true, false, null),
    TELEGRAM(true, false, SocialMediaEnum.TELEGRAM),
    VIBER(true, false, SocialMediaEnum.VIBER),
    WHATSAPP(true, false, SocialMediaEnum.WHATSAPP),
    FACEBOOK(true, false, SocialMediaEnum.FACEBOOK);

    private final boolean needPhone;
    private final boolean needEmail;
    private final @Nullable SocialMediaEnum social;

    NotifyChannel(boolean needPhone, boolean needEmail, @Nullable SocialMediaEnum social) {
        this.needPhone = needPhone;
        this.needEmail = needEmail;
        this.social = social;
    }

    public @Nullable SocialMediaEnum social() {
        return social;
    }

    public boolean isNeedPhone() {
        return needPhone;
    }

    public boolean isNeedEmail() {
        return needEmail;
    }
}
