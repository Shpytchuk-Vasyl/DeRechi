package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.channel.BotApiClient;
import org.shpytchuk.telegramingest.http.HttpCalls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Bean
    public BotApiClient botApiClient(BotProperties properties) {
        return new BotApiClient(HttpCalls.restClient(properties.timeout()), properties);
    }
}
