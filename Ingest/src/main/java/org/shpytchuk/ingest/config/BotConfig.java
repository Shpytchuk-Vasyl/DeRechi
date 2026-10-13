package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.publish.telegram.BotApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Bean
    public BotApiClient botApiClient(BotProperties properties) {
        return new BotApiClient(HttpCalls.restClient(properties.timeout()), properties);
    }
}
