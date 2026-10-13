package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.config.bot.BotProperties;
import org.shpytchuk.ingest.config.client.ApiProperties;
import org.shpytchuk.ingest.config.openrouter.OpenRouterProperties;
import org.shpytchuk.ingest.config.places.PlacesProperties;
import org.shpytchuk.ingest.config.redaction.RedactionProperties;
import org.shpytchuk.ingest.config.storage.StorageProperties;
import org.shpytchuk.ingest.config.telegram.TelegramProperties;
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
