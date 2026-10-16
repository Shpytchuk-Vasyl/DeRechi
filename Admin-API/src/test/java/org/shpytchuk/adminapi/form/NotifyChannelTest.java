package org.shpytchuk.adminapi.form;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    /** Messengers are addressed by the phone number, so only EMAIL does without it; only ALL and EMAIL need the email. */
    @Test
    void eachChannelAsksForTheContactsItDeliversTo() {
        assertThat(NotifyChannel.values())
                .extracting(NotifyChannel::name, NotifyChannel::isNeedPhone, NotifyChannel::isNeedEmail)
                .containsExactly(
                        tuple("ALL", true, true),
                        tuple("EMAIL", false, true),
                        tuple("PHONE", true, false),
                        tuple("TELEGRAM", true, false),
                        tuple("VIBER", true, false),
                        tuple("WHATSAPP", true, false),
                        tuple("FACEBOOK", true, false));
    }
}
