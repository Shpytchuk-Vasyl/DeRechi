package org.shpytchuk.notification;

import io.notifyhub.core.NotifyHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "derechi.sms-fly.api-key=test-key")
class SmsFlyRegistrationTests {

    @Autowired
    private NotifyHub notifyHub;

    @Test
    void notifyHubPicksUpTheSmsFlyChannelAsSms() {
        assertThat(notifyHub.getRegisteredChannels()).contains("email", "sms");
    }
}
