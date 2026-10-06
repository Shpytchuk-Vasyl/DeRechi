package org.shpytchuk.notification.channel;

import io.notifyhub.core.Notification;
import io.notifyhub.core.channel.NotificationSendException;
import io.notifyhub.core.channel.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SmsFlyChannelTest {

    private static final String API = "https://sms-fly.ua/api/v2/api.php";
    private static final String ACCEPTED = """
            {"success":1,"date":"2021-12-17 10:36:07 +0200",
             "data":{"messageID":"FAPI00040A3AFA000002","sms":{"status":"ACCEPTD","date":"2021-12-17 10:36:07 +0200","cost":0.475}}}
            """;

    private MockRestServiceServer server;
    private SmsFlyChannel channel;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        channel = SmsFlyChannel.of(builder, new SmsFlyProperties(API, "api-key", "DeRechi", Duration.ofHours(24),
                Duration.ofSeconds(3), Duration.ofSeconds(10)));
    }

    @Test
    void isTheSmsChannelOfNotifyHub() {
        assertThat(channel.getName()).isEqualTo("sms");
    }

    @Test
    void sendsAnSmsOnlyMessageWithTheKeyAndTheAlphaName() {
        server.expect(requestTo(API))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.auth.key").value("api-key"))
                .andExpect(jsonPath("$.action").value("SENDMESSAGE"))
                .andExpect(jsonPath("$.data.recipient").value("380501234567"))
                .andExpect(jsonPath("$.data.channels").value("sms"))
                .andExpect(jsonPath("$.data.sms.source").value("DeRechi"))
                .andExpect(jsonPath("$.data.sms.ttl").value(1440))
                .andExpect(jsonPath("$.data.sms.text").value("Схоже, ми знайшли вашу річ"))
                .andExpect(jsonPath("$.data.viber").doesNotExist())
                .andRespond(withSuccess(ACCEPTED, MediaType.APPLICATION_JSON));

        SendResult result = channel.sendWithResult(sms("+380501234567"));

        assertThat(result.getProviderMessageId()).isEqualTo("FAPI00040A3AFA000002");
        server.verify();
    }

    @Test
    void readsJsonServedAsHtml() {
        server.expect(requestTo(API))
                .andRespond(withSuccess(ACCEPTED, MediaType.TEXT_HTML));

        assertThat(channel.sendWithResult(sms("+380501234567")).getProviderMessageId())
                .isEqualTo("FAPI00040A3AFA000002");
    }

    @Test
    void reportsAnAnswerThatIsNotJson() {
        server.expect(requestTo(API))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> channel.send(sms("+380501234567")))
                .isInstanceOf(NotificationSendException.class)
                .hasMessageContaining("maintenance");
    }

    @Test
    void stripsEverythingButDigitsFromThePhone() {
        assertThat(SmsFlyChannel.recipient("+380 (50) 123-45-67")).isEqualTo("380501234567");
        assertThat(SmsFlyChannel.recipient("+48 512 345 678")).isEqualTo("48512345678");
    }

    @Test
    void refusesToSendWithoutAPhone() {
        assertThatThrownBy(() -> channel.send(sms(null)))
                .isInstanceOf(NotificationSendException.class)
                .hasMessageContaining("No phone number");
    }

    @Test
    void reportsAnApiErrorWithItsCode() {
        server.expect(requestTo(API))
                .andRespond(withSuccess("""
                        {"success":0,"error":{"code":"INVSOURCE","date":"2021-02-03 04:05:06 +0200","description":"unknown sender"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> channel.send(sms("+380501234567")))
                .isInstanceOf(NotificationSendException.class)
                .hasMessageContaining("INVSOURCE")
                .hasMessageContaining("unknown sender");
    }

    @Test
    void reportsAMessageTheProviderDidNotAccept() {
        server.expect(requestTo(API))
                .andRespond(withSuccess("""
                        {"success":1,"data":{"messageID":"FAPI1","sms":{"status":"INSUFFICIENTFUNDS"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> channel.send(sms("+380501234567")))
                .isInstanceOf(NotificationSendException.class)
                .hasMessageContaining("INSUFFICIENTFUNDS");
    }

    @Test
    void reportsAnHttpErrorWithItsBody() {
        server.expect(requestTo(API))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":0,\"error\":{\"code\":\"FORBIDDEN\"}}"));

        assertThatThrownBy(() -> channel.send(sms("+380501234567")))
                .isInstanceOf(NotificationSendException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    void rejectsATtlSmsFlyDoesNotAccept() {
        assertThatThrownBy(() -> new SmsFlyProperties(API, "api-key", "DeRechi", Duration.ofHours(25),
                Duration.ofSeconds(3), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }

    @Test
    void hidesTheKeyInToString() {
        assertThat(new SmsFlyProperties(API, "api-key", "DeRechi", Duration.ofHours(1),
                Duration.ofSeconds(3), Duration.ofSeconds(10)).toString())
                .doesNotContain("api-key");
    }

    private static Notification sms(String phone) {
        return new Notification(phone, "sms", "Знахідка", null, "Схоже, ми знайшли вашу річ", Map.of());
    }
}
