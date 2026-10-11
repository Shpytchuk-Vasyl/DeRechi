package org.shpytchuk.telegramingest.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableCaching
@EnableConfigurationProperties({
        TelegramProperties.class,
        BotProperties.class,
        IngestProperties.class,
        OpenRouterProperties.class,
        PlacesProperties.class,
        ApiProperties.class,
        StorageProperties.class,
        RedactionProperties.class
})
public class IngestConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
