package org.shpytchuk.ingest.config.openrouter;

import org.shpytchuk.ingest.client.HttpCalls;
import org.shpytchuk.ingest.config.IngestProperties;
import org.shpytchuk.ingest.extraction.PromptBuilder;
import org.shpytchuk.ingest.extraction.openrouter.DailyCallCounter;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterClient;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterExtractor;
import org.shpytchuk.ingest.extraction.openrouter.OpenRouterOwnerNameFinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class OpenRouterConfig {

    @Bean
    public OpenRouterClient openRouterClient(OpenRouterProperties properties, IngestProperties ingest,
                                             DailyCallCounter counter, JsonMapper jsonMapper) {
        return new OpenRouterClient(HttpCalls.restClient(properties.timeout()), properties, ingest, counter, jsonMapper);
    }

    @Bean
    public OpenRouterExtractor openRouterExtractor(OpenRouterClient client, PromptBuilder prompts) {
        return new OpenRouterExtractor(client, prompts);
    }

    @Bean
    public OpenRouterOwnerNameFinder openRouterOwnerNameFinder(OpenRouterClient client, PromptBuilder prompts) {
        return new OpenRouterOwnerNameFinder(client, prompts);
    }
}
