package org.shpytchuk.notification.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.notification.channel.SmsFlyChannel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SmsFlyConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SmsFlyConfig.class)
            .withPropertyValues(
                    "derechi.sms-fly.api-url=https://sms-fly.ua/api/v2/api.php",
                    "derechi.sms-fly.source=DeRechi",
                    "derechi.sms-fly.ttl=24h",
                    "derechi.sms-fly.connect-timeout=3s",
                    "derechi.sms-fly.read-timeout=10s");

    @Test
    void registersTheChannelWhenTheKeyIsSet() {
        runner.withPropertyValues("derechi.sms-fly.api-key=secret")
                .run(context -> assertThat(context).hasSingleBean(SmsFlyChannel.class));
    }

    @Test
    void failsTheStartWithoutAnAlphaName() {
        runner.withPropertyValues("derechi.sms-fly.api-key=secret", "derechi.sms-fly.source=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void staysOffWithAnEmptyKey() {
        runner.withPropertyValues("derechi.sms-fly.api-key=")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SmsFlyChannel.class));
    }

    @Test
    void staysOffWithoutAKey() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(SmsFlyChannel.class));
    }
}
