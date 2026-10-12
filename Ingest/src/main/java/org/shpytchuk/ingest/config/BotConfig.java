package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.channel.BotApiClient;
import org.shpytchuk.ingest.http.HttpCalls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Bean
    public BotApiClient botApiClient(BotProperties properties) {
        return new BotApiClient(HttpCalls.restClient(properties.timeout()), properties);
    }
}
