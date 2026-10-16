package org.shpytchuk.adminapi.view.detail;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

import static org.assertj.core.api.Assertions.assertThat;

class SocialMediaIconsTest {

    private final SocialMediaIcons icons = new SocialMediaIcons();

    @Test
    void everySocialMediaHasABrandIcon() {
        for (SocialMediaEnum social : SocialMediaEnum.values()) {
            assertThat(icons.icon(social)).as(social.name()).startsWith("fa-brands ");
        }
        assertThat(icons.icon(SocialMediaEnum.FACEBOOK)).isEqualTo("fa-brands fa-facebook");
    }

    @Test
    void fallsBackToAGenericIconWithoutANetwork() {
        assertThat(icons.icon(null)).isEqualTo("fa-solid fa-comment-dots");
    }
}
