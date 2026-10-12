package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.http.HttpCalls;
import org.shpytchuk.ingest.api.ai.llm.DailyCallCounter;
import org.shpytchuk.ingest.api.ai.llm.OpenRouterExtractor;
import org.shpytchuk.ingest.api.ai.llm.PromptBuilder;
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
