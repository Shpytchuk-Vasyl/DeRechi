package org.shpytchuk.ingest.config;

import org.shpytchuk.ingest.api.DeRechiApi;
import org.shpytchuk.ingest.api.GraphQlDeRechiApi;
import org.shpytchuk.ingest.http.HttpCalls;
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
