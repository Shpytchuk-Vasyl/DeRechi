package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.config.GraphQlConfig;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.service.ReferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(controllers = ReferenceController.class,
        properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@Import({ReferenceService.class, GraphQlConfig.class})
@EnableConfigurationProperties(CountriesProperties.class)
class ReferenceControllerSliceTests {

    @Autowired
    private GraphQlTester tester;

    @MockitoBean
    private ThingCategoryRepository categoryRepository;

    @MockitoBean
    private PlaceRepository placeRepository;

    @Test
    void returnsTheConfiguredCountriesInOrderWithTheirCurrencies() {
        tester.document("{ countries { code currency } }")
                .execute()
                .path("countries[*].code").entityList(String.class)
                .containsExactly("UA", "PL", "DE", "FR")
                .path("countries[*].currency").entityList(String.class)
                .containsExactly("UAH", "PLN", "EUR", "EUR");
    }

    @Test
    void sharesTheCurrencyBetweenEuroCountries() {
        tester.document("{ countries { code currency } }")
                .execute()
                .path("countries[?(@.currency == 'EUR')].code").entityList(String.class)
                .containsExactly("DE", "FR");
    }
}
