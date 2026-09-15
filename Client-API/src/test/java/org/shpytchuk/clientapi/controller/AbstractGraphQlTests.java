package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.shpytchuk.clientapi.entity.ThingCategory;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.shpytchuk.clientapi.support.AbstractPostgresTests;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureGraphQlTester
abstract class AbstractGraphQlTests extends AbstractPostgresTests {

    @Autowired
    protected GraphQlTester tester;

    @Autowired
    private ThingCategoryRepository categoryRepository;

    /** The event publisher is an aspect on the services, so the broker has to be stubbed out. */
    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

    protected ThingCategory documents;
    protected ThingCategory wallet;

    @BeforeEach
    void loadSeededCategories() {
        documents = categoryByKey("DOCUMENTS");
        wallet = categoryByKey("WALLET");
    }

    protected ThingCategory categoryByKey(String key) {
        return categoryRepository.findAll().stream()
                .filter(category -> key.equals(category.getKey()))
                .findFirst()
                .orElseThrow();
    }

    protected Map<String, Object> input(String title, Long categoryId) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", title);
        input.put("description", "Звʼязка з брелоком");
        input.put("date", LocalDate.now().toString());
        input.put("compensation", 500);
        input.put("image", "keys.png");
        input.put("categoryId", String.valueOf(categoryId));
        input.put("place", place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        input.put("contact", contact("+380671234567"));
        return input;
    }

    protected static Map<String, Object> place(String id, String name, double lat, double lon) {
        return Map.of("id", id, "name", name, "lat", lat, "lon", lon);
    }

    protected static Map<String, Object> contact(String phone) {
        return Map.of(
                "phone", phone,
                "email", "finder@example.com",
                "socialMedias", List.of("TELEGRAM"));
    }
}
