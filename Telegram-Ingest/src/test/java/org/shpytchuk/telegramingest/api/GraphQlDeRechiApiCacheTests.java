package org.shpytchuk.telegramingest.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.telegramingest.config.ApiProperties;
import org.shpytchuk.telegramingest.exception.IngestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The {@code @Cacheable} reference lookups through the Spring proxy, as the pipeline calls them. */
@SpringJUnitConfig(GraphQlDeRechiApiCacheTests.Config.class)
class GraphQlDeRechiApiCacheTests {

    static final String URL = "http://gateway/graphql";

    @Autowired
    DeRechiApi api;

    @Autowired
    MockRestServiceServer server;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void reset() {
        server.reset();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void categoriesAreLoadedOnceAndThenServedFromTheCache() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"categories\":[{\"id\":\"1\",\"key\":\"DOCUMENTS\"},{\"id\":\"2\",\"key\":\"KEYS\"}]}}",
                MediaType.APPLICATION_JSON));

        assertThat(api.categories()).isEqualTo(Map.of("DOCUMENTS", 1L, "KEYS", 2L));
        assertThat(api.categories()).containsEntry("KEYS", 2L);
        server.verify();
    }

    @Test
    void categoriesAndCountriesAreCachedSeparately() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"categories\":[{\"id\":\"1\",\"key\":\"DOCUMENTS\"}]}}", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"countries\":[{\"code\":\"UA\"},{\"code\":\"PL\"}]}}", MediaType.APPLICATION_JSON));

        api.categories();
        assertThat(api.countries()).isEqualTo(Set.of("UA", "PL"));
        assertThat(api.countries()).isEqualTo(Set.of("UA", "PL"));
        server.verify();
    }

    @Test
    void aFailedLoadIsNotCached() {
        server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withServiceUnavailable());
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(
                "{\"data\":{\"countries\":[{\"code\":\"UA\"}]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.countries()).isInstanceOf(IngestException.class);
        assertThat(api.countries()).containsExactly("UA");
        server.verify();
    }

    @Configuration
    @EnableCaching
    static class Config {

        private final RestClient.Builder restClient = RestClient.builder().baseUrl(URL);

        @Bean
        MockRestServiceServer server() {
            return MockRestServiceServer.bindTo(restClient).build();
        }

        @Bean
        DeRechiApi api(MockRestServiceServer server) {
            return new GraphQlDeRechiApi(HttpSyncGraphQlClient.builder(restClient).build(),
                    new ApiProperties("http://gateway", Duration.ofSeconds(5), 3, Duration.ofMillis(1)));
        }

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager(
                    GraphQlDeRechiApi.CATEGORIES_CACHE, GraphQlDeRechiApi.COUNTRIES_CACHE);
            manager.setCacheSpecification("expireAfterWrite=1h");
            return manager;
        }
    }
}
