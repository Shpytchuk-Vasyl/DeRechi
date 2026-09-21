package org.shpytchuk.adminapi.form;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyChannelTest {

    @Test
    void everySocialMediaHasItsOwnChannel() {
        for (SocialMediaEnum social : SocialMediaEnum.values()) {
            assertThat(NotifyChannel.valueOf(social.name()).social()).isEqualTo(social);
        }
    }

    @Test
    void contactChannelsAreNotSocial() {
        assertThat(NotifyChannel.ALL.social()).isNull();
        assertThat(NotifyChannel.EMAIL.social()).isNull();
        assertThat(NotifyChannel.PHONE.social()).isNull();
    }
}
