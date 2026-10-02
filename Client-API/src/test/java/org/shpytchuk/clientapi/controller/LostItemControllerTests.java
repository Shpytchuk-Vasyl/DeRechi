package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.ErrorType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LostItemControllerTests extends AbstractGraphQlTests {

    private static final String CREATE = """
            mutation Create($input: ItemInput!) {
              createLostItem(input: $input) {
                id title description date image
                compensation { amount currency }
                category { id key }
                place { id name lat lon countryCode }
                contact { id phone email }
              }
            }
            """;

    @Autowired
    private LostItemRepository lostItemRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    void createsLostItemAndReturnsTheStoredShape() {
        tester.document(CREATE)
                .variable("input", input("Ключі", documents.getId()))
                .execute()
                .path("createLostItem.title").entity(String.class).isEqualTo("Ключі")
                .path("createLostItem.date").entity(String.class)
                .isEqualTo(LocalDate.now().toString())
                .path("createLostItem.compensation.amount").entity(Integer.class).isEqualTo(500)
                .path("createLostItem.compensation.currency").entity(String.class).isEqualTo("UAH")
                .path("createLostItem.category.key").entity(String.class).isEqualTo("DOCUMENTS")
                .path("createLostItem.place.lat").entity(Double.class).isEqualTo(49.8419)
                .path("createLostItem.place.lon").entity(Double.class).isEqualTo(24.0315)
                .path("createLostItem.place.countryCode").entity(String.class).isEqualTo("UA")
                .path("createLostItem.contact.phone").entity(String.class)
                .isEqualTo("+38067*****67")
                .path("createLostItem.contact.email").entity(String.class)
                .isEqualTo("f*****r@example.com");
    }

    @Test
    void createsLostItemRowInThDatabase() {
        Long id = tester.document(CREATE)
                .variable("input", input("Ключі", documents.getId()))
                .execute()
                .path("createLostItem.id").entity(Long.class).get();

        LostItem stored = lostItemRepository.findWithDetailsById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Ключі");
        assertThat(stored.getInfo().getSocialMedias()).containsExactly(SocialMediaEnum.TELEGRAM);
        assertThat(stored.getCurrency()).isEqualTo("UAH");
        assertThat(placeRepository.findById("ChIJrynok")).map(place -> place.getCountryCode()).hasValue("UA");
    }

    @Test
    void publishesTheCreatedEventThroughTheAspect() {
        tester.document(CREATE)
                .variable("input", input("Ключі", documents.getId()))
                .execute()
                .path("createLostItem.id").hasValue();

        verify(rabbitTemplate).convertAndSend(
                eq("derechi.items"), eq("item.lost.created"), any(ItemCreatedEvent.class));
    }

    @Test
    void readsBackTheItemItJustCreated() {
        Long id = createKeys();

        tester.document("""
                        query Read($id: ID!) {
                          lostItem(id: $id) {
                            id title place { name } contact { phone } category { key }
                          }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("lostItem.title").entity(String.class).isEqualTo("Ключі")
                .path("lostItem.place.name").entity(String.class).isEqualTo("Площа Ринок")
                .path("lostItem.contact.phone").entity(String.class).isEqualTo("+38067*****67")
                .path("lostItem.category.key").entity(String.class).isEqualTo("DOCUMENTS");
    }

    @Test
    void returnsNullForAnUnknownId() {
        tester.document("{ lostItem(id: -1) { id } }")
                .execute()
                .path("lostItem").valueIsNull();
    }

    @Test
    void listsCreatedItemsAsAConnection() {
        createKeys();
        create("Гаманець", wallet.getId());

        tester.document("""
                        { lostItems(first: 10) {
                            edges { cursor node { title } }
                            pageInfo { hasNextPage endCursor }
                        } }
                        """)
                .execute()
                .path("lostItems.edges").entityList(Object.class).hasSize(2)
                .path("lostItems.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void filtersBySearchTermAgainstTheDatabase() {
        createKeys();
        create("Гаманець", wallet.getId());

        tester.document("""
                        query Search($filter: ItemFilterInput) {
                          lostItems(filter: $filter, first: 10) { edges { node { title } } }
                        }
                        """)
                .variable("filter", Map.of("search", "КЛЮЧ"))
                .execute()
                .path("lostItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Ключі");
    }

    @Test
    void filtersByCategoryAgainstTheDatabase() {
        createKeys();
        create("Гаманець", wallet.getId());

        tester.document("""
                        query Search($filter: ItemFilterInput) {
                          lostItems(filter: $filter, first: 10) { edges { node { title } } }
                        }
                        """)
                .variable("filter", Map.of("categoryId", String.valueOf(wallet.getId())))
                .execute()
                .path("lostItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Гаманець");
    }

    @Test
    void filtersByDistanceAgainstTheDatabase() {
        createKeys();

        String query = """
                query Search($filter: ItemFilterInput) {
                  lostItems(filter: $filter, first: 10) { edges { node { title } } }
                }
                """;

        tester.document(query)
                .variable("filter", Map.of("near", Map.of("lat", 49.84, "lon", 24.03)))
                .execute()
                .path("lostItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Ключі");

        tester.document(query)
                .variable("filter", Map.of("near", Map.of("lat", 50.4501, "lon", 30.5234)))
                .execute()
                .path("lostItems.edges").entityList(Object.class).hasSize(0);
    }

    @Test
    void rejectsAFilterPointOutsideTheGlobe() {
        tester.document("""
                        query Search($filter: ItemFilterInput) {
                          lostItems(filter: $filter, first: 10) { edges { node { title } } }
                        }
                        """)
                .variable("filter", Map.of("near", Map.of("lat", 91.0, "lon", 24.03)))
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("lat");
                });
    }

    @Test
    void sortsByTitleAscending() {
        create("Бандана", documents.getId());
        create("Ананас", documents.getId());

        tester.document("{ lostItems(sort: TITLE_ASC, first: 10) { edges { node { title } } } }")
                .execute()
                .path("lostItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Ананас", "Бандана");
    }

    @Test
    void pagesThroughTheConnectionWithTheReturnedCursor() {
        create("Ананас", documents.getId());
        create("Бандана", documents.getId());
        create("Вудка", documents.getId());

        String cursor = tester
                .document("{ lostItems(sort: TITLE_ASC, first: 2) { pageInfo { endCursor hasNextPage } } }")
                .execute()
                .path("lostItems.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true)
                .path("lostItems.pageInfo.endCursor").entity(String.class).get();

        tester.document("""
                        query Page($after: String) {
                          lostItems(sort: TITLE_ASC, first: 2, after: $after) {
                            edges { node { title } }
                            pageInfo { hasNextPage }
                          }
                        }
                        """)
                .variable("after", cursor)
                .execute()
                .path("lostItems.edges[*].node.title").entityList(String.class)
                .containsExactly("Вудка");
    }

    @Test
    void rejectsPhoneThatIsNotE164() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("contact", contact("0671234567"));

        expectBadRequest(input, "phone");
        assertThat(lostItemRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsInvalidEmail() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("contact", Map.of(
                "phone", "+380671234567",
                "email", "not-an-email",
                "socialMedias", List.of("TELEGRAM")));

        expectBadRequest(input, "email");
    }

    @Test
    void rejectsFutureDate() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("date", LocalDate.now().plusDays(1).toString());

        expectBadRequest(input, "date");
    }

    @Test
    void rejectsDateOlderThanThirtyDays() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("date", LocalDate.now().minusDays(31).toString());

        expectBadRequest(input, "date");
    }

    @Test
    void rejectsNegativeCompensation() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("compensation", Map.of("amount", -1));

        expectBadRequest(input, "compensation");
    }

    @Test
    void defaultsTheCurrencyToTheOneOfThePlaceCountry() {
        Map<String, Object> input = input("Klucze", documents.getId());
        input.put("place", place("ChIJkrakow", "Rynek Glowny", 50.0617, 19.9373, "PL"));

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .path("createLostItem.compensation.amount").entity(Integer.class).isEqualTo(500)
                .path("createLostItem.compensation.currency").entity(String.class).isEqualTo("PLN")
                .path("createLostItem.place.countryCode").entity(String.class).isEqualTo("PL");
    }

    @Test
    void acceptsAnExplicitSupportedCurrencyAndUppercasesTheCodes() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("compensation", Map.of("amount", 20, "currency", "eur"));
        input.put("place", place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315, "ua"));

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .path("createLostItem.compensation.currency").entity(String.class).isEqualTo("EUR")
                .path("createLostItem.place.countryCode").entity(String.class).isEqualTo("UA");
    }

    @Test
    void keepsTheCurrencyWhenThereIsNoCompensation() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.remove("compensation");

        Long id = tester.document(CREATE)
                .variable("input", input)
                .execute()
                .path("createLostItem.compensation").valueIsNull()
                .path("createLostItem.id").entity(Long.class).get();

        assertThat(lostItemRepository.findById(id).orElseThrow().getCurrency()).isEqualTo("UAH");
    }

    @Test
    void rejectsAnUnsupportedCountry() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("place", place("ChIJsydney", "Sydney", -33.8688, 151.2093, "AU"));

        expectBadRequest(input, "Unsupported country: AU");
        assertThat(lostItemRepository.findAll()).isEmpty();
        assertThat(placeRepository.findById("ChIJsydney")).isEmpty();
    }

    @Test
    void rejectsAnUnsupportedCurrency() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("compensation", Map.of("amount", 500, "currency", "USD"));

        expectBadRequest(input, "Unsupported currency: USD");
        assertThat(lostItemRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsACountryCodeThatIsNotTwoLetters() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("place", place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315, "UKR"));

        expectBadRequest(input, "countryCode");
    }

    @Test
    void rejectsCoordinateOutsideTheGlobe() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("place", place("ChIJrynok", "Площа Ринок", 91.0, 24.0315));

        expectBadRequest(input, "lat");
    }

    @Test
    void doesNotPublishAnEventWhenValidationFails() {
        Map<String, Object> input = input("Ключі", documents.getId());
        input.put("contact", contact("0671234567"));

        expectBadRequest(input, "phone");

        verify(rabbitTemplate, never())
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void reportsAnUnknownCategoryAsNotFound() {
        Map<String, Object> input = input("Ключі", -1L);

        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    assertThat(errors.getFirst().getMessage()).contains("Category.id: -1");
                });
    }

    @Test
    void rejectsAPageSizeOverTheServiceCap() {
        tester.document("{ lostItems(first: 500) { edges { node { id } } } }")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains("1..100");
                });
    }

    private void expectBadRequest(Map<String, Object> input, String expectedPath) {
        tester.document(CREATE)
                .variable("input", input)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.getFirst().getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    assertThat(errors.getFirst().getMessage()).contains(expectedPath);
                });
    }

    private Long createKeys() {
        return create("Ключі", documents.getId());
    }

    private Long create(String title, Long categoryId) {
        return tester.document(CREATE)
                .variable("input", input(title, categoryId))
                .execute()
                .path("createLostItem.id").entity(Long.class).get();
    }
}
