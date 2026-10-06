package org.shpytchuk.notification.service;

import io.notifyhub.core.NotifyHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.channel.SmsFlyChannel;
import org.shpytchuk.notification.channel.SmsFlyProperties;
import org.shpytchuk.notification.event.NotificationRequestedEvent;
import org.shpytchuk.notification.event.NotificationRequestedEvent.SocialMediaEnum;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationSenderSmsFlyTest {

    private static final String API = "https://sms-fly.ua/api/v2/api.php";

    private MockRestServiceServer server;
    private NotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SmsFlyChannel channel = SmsFlyChannel.of(builder, new SmsFlyProperties(API, "api-key", "DeRechi",
                Duration.ofHours(24), Duration.ofSeconds(3), Duration.ofSeconds(10)));
        sender = new NotificationSender(NotifyHub.builder().channel(channel).build());
    }

    @Test
    void sendsTheEventPhoneThroughSmsFly() {
        server.expect(requestTo(API))
                .andExpect(jsonPath("$.data.recipient").value("380501234567"))
                .andExpect(jsonPath("$.data.sms.text").value("Схоже, ми знайшли вашу річ"))
                .andRespond(withSuccess("""
                        {"success":1,"data":{"messageID":"FAPI1","sms":{"status":"ACCEPTD","cost":"1.289"}}}
                        """, MediaType.APPLICATION_JSON));

        sender.send(event());

        server.verify();
    }

    @Test
    void letsARejectedSmsFailTheDelivery() {
        server.expect(ExpectedCount.manyTimes(), requestTo(API))
                .andRespond(withSuccess("""
                        {"success":0,"error":{"code":"INVSOURCE","description":""}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender.send(event()))
                .hasStackTraceContaining("INVSOURCE");
    }

    private static NotificationRequestedEvent event() {
        return new NotificationRequestedEvent("Знахідка", "Схоже, ми знайшли вашу річ", "+380501234567", null,
                new SocialMediaEnum[0], "match:1:2");
    }
}
