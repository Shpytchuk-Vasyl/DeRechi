package org.shpytchuk.ingest.config.openrouter;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.extraction.PromptBuilder;
import org.shpytchuk.ingest.extraction.openrouter.DailyCallCounter;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class OpenRouterConfig {

    @Bean
    public OpenRouterExtractor openRouterExtractor(OpenRouterProperties properties, IngestProperties ingest,
                                                   PromptBuilder prompts, DailyCallCounter counter,
                                                   JsonMapper jsonMapper) {
        return new OpenRouterExtractor(HttpCalls.restClient(properties.timeout()), properties, ingest, prompts,
                counter, jsonMapper);
    }
}
