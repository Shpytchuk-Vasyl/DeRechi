package org.shpytchuk.adminapi.view.detail;

import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("social")
public class SocialMediaIcons {

    private static final String FALLBACK = "fa-solid fa-comment-dots";

    private static final Map<SocialMediaEnum, String> ICONS = Map.of(
            SocialMediaEnum.TELEGRAM, "fa-brands fa-telegram",
            SocialMediaEnum.VIBER, "fa-brands fa-viber",
            SocialMediaEnum.WHATSAPP, "fa-brands fa-whatsapp");

    public String icon(SocialMediaEnum social) {
        return ICONS.getOrDefault(social, FALLBACK);
    }
}
