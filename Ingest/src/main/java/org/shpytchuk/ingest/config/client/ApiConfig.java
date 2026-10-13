package org.shpytchuk.ingest.config.client;

import org.shpytchuk.ingest.client.DeRechiApi;
import org.shpytchuk.ingest.client.GraphQlDeRechiApi;
import org.shpytchuk.ingest.client.HttpCalls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpSyncGraphQlClient;

@Configuration
public class ApiConfig {

    @Bean
    public DeRechiApi deRechiApi(ApiProperties properties) {
        HttpSyncGraphQlClient client = HttpSyncGraphQlClient
                .builder(HttpCalls.restClient(properties.timeout()).baseUrl(properties.uri() + "/graphql"))
                .build();
        return new GraphQlDeRechiApi(client, properties);
    }
}
