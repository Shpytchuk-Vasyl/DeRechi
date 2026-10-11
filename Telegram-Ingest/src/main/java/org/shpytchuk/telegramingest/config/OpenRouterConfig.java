package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.http.HttpCalls;
import org.shpytchuk.telegramingest.llm.DailyCallCounter;
import org.shpytchuk.telegramingest.llm.OpenRouterExtractor;
import org.shpytchuk.telegramingest.llm.PromptBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
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
