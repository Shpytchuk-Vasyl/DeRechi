package org.shpytchuk.telegramingest.config;

import org.shpytchuk.telegramingest.api.DeRechiApi;
import org.shpytchuk.telegramingest.api.GraphQlDeRechiApi;
import org.shpytchuk.telegramingest.http.HttpCalls;
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
