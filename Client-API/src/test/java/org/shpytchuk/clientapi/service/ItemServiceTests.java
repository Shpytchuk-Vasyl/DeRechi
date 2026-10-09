package org.shpytchuk.clientapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.ItemSort;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.ThingCategory;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.input.*;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.service.found.FoundItemService;
import org.shpytchuk.clientapi.service.lost.LostItemService;
import org.shpytchuk.clientapi.support.AbstractPostgresTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.query.ScrollSubrange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.shpytchuk.clientapi.support.Fixtures.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LostItemService.class, FoundItemService.class, ReferenceService.class})
@EnableConfigurationProperties(CountriesProperties.class)
class ItemServiceTests extends AbstractPostgresTests {

    private static final LocalDate SEPTEMBER_1 = LocalDate.of(2026, 9, 1);

    @Autowired
    private LostItemService lostService;

    @Autowired
    private FoundItemService foundService;

    @Autowired
    private LostItemRepository lostItemRepository;

    @Autowired
    private FoundItemRepository foundItemRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ContactInfoRepository contactInfoRepository;

    @Autowired
    private ThingCategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ThingCategory documents;
    private ThingCategory wallet;

    @BeforeEach
    void loadSeededCategories() {
        documents = categoryByKey("DOCUMENTS");
        wallet = categoryByKey("WALLET");
    }

    @Nested
    class Create {

        @Test
        void insertsTheItemWithItsPlaceAndContact() {
            ItemDto dto = lostService.create(input("keys.png", documents.getId()));
            entityManager.flush();
            entityManager.clear();

            LostItem stored = lostItemRepository.findWithDetailsById(dto.id()).orElseThrow();

            assertThat(stored.getTitle()).isEqualTo("Ключі");
            assertThat(stored.getDate()).isEqualTo(SEPTEMBER_1);
            assertThat(stored.getCompensation()).isEqualTo(500);
            assertThat(stored.getCurrency()).isEqualTo("UAH");
            assertThat(stored.getImage()).isEqualTo("keys.png");
            assertThat(stored.getCategory().getKey()).isEqualTo("DOCUMENTS");
            assertThat(stored.getPlace().getGooglePlaceId()).isEqualTo("ChIJplaceId");
            assertThat(stored.getPlace().getCountryCode()).isEqualTo("UA");
            assertThat(stored.getInfo().getPhone()).isEqualTo("+380671234567");
        }

        @Test
        void assignsAGeneratedIdentifier() {
            ItemDto dto = lostService.create(input("keys.png", documents.getId()));

            assertThat(dto.id()).isNotNull().isPositive();
        }

        @Test
        void failsWhenCategoryIsUnknown() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> lostService.create(input("keys.png", -1L)))
                    .withMessageContaining("Category.id: -1");
        }

        @Test
        void requiresImageForFoundItems() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> foundService.create(input(null, documents.getId())));
        }

        @Test
        void treatsBlankImageAsMissingForFoundItems() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> foundService.create(input("   ", documents.getId())));
        }

        @Test
        void allowsLostItemWithoutImage() {
            ItemDto dto = lostService.create(input(null, documents.getId()));
            entityManager.flush();
            entityManager.clear();

            assertThat(lostItemRepository.findWithDetailsById(dto.id()).orElseThrow().getImage())
                    .isNull();
        }
    }

    @Nested
    class Money {

        @Test
        void derivesTheCurrencyFromThePlaceCountryWhenNoneIsGiven() {
            ItemDto dto = lostService.create(input(new MoneyInput(200, null), "PL"));

            assertThat(dto.compensation().amount()).isEqualTo(200);
            assertThat(dto.compensation().currency()).isEqualTo("PLN");
            assertThat(dto.place().countryCode()).isEqualTo("PL");
        }

        @Test
        void acceptsAnExplicitSupportedCurrency() {
            ItemDto dto = lostService.create(input(new MoneyInput(200, "PLN"), "UA"));

            assertThat(dto.compensation().currency()).isEqualTo("PLN");
        }

        @Test
        void uppercasesCountryAndCurrencyCodes() {
            ItemDto dto = lostService.create(input(new MoneyInput(200, "eur"), "de"));
            entityManager.flush();
            entityManager.clear();

            LostItem stored = lostItemRepository.findWithDetailsById(dto.id()).orElseThrow();
            assertThat(stored.getCurrency()).isEqualTo("EUR");
            assertThat(stored.getPlace().getCountryCode()).isEqualTo("DE");
        }

        @Test
        void storesTheCountryCurrencyEvenWithoutCompensation() {
            ItemDto dto = lostService.create(input(null, "FR"));
            entityManager.flush();
            entityManager.clear();

            assertThat(dto.compensation()).isNull();
            assertThat(lostItemRepository.findById(dto.id()).orElseThrow().getCurrency()).isEqualTo("EUR");
        }

        @Test
        void rejectsAnUnsupportedCountry() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> lostService.create(input(new MoneyInput(200, null), "AU")))
                    .withMessage("Unsupported country: AU");
            assertThat(placeRepository.findById("ChIJplaceId")).isEmpty();
        }

        @Test
        void rejectsAnUnsupportedCurrency() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> lostService.create(input(new MoneyInput(200, "USD"), "UA")))
                    .withMessage("Unsupported currency: USD");
            assertThat(lostItemRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class FindById {

        @Test
        void returnsEmptyWhenAbsent() {
            assertThat(lostService.findById(-1L)).isEmpty();
        }

        @Test
        void doesNotSeeFoundItemsThroughTheLostService() {
            Long id = foundService.create(input("umbrella.png", documents.getId())).id();
            entityManager.flush();
            entityManager.clear();

            assertThat(lostService.findById(id)).isEmpty();
        }
    }

    @Nested
    class FindAll {

        @Test
        void returnsEverythingWhenTheFilterIsNull() {
            persistLost("Ключі", SEPTEMBER_1, documents);
            persistLost("Гаманець", SEPTEMBER_1, wallet);
            entityManager.flush();

            Window<ItemDto> window = lostService.findAll(null, ItemSort.DATE_DESC, subrange(null, null));

            assertThat(window.getContent()).hasSize(2);
        }

        @Test
        void reportsNoNextPageOnTheLastOne() {
            persistLost("Ключі", SEPTEMBER_1, documents);
            entityManager.flush();

            Window<ItemDto> window = lostService.findAll(
                    ItemFilterInput.EMPTY, ItemSort.DATE_DESC, subrange(null, 20));

            assertThat(window.hasNext()).isFalse();
        }

        @Test
        void rejectsAKeysetCursor() {
            ScrollPosition keyset = ScrollPosition.of(Map.of("id", 1L), ScrollPosition.Direction.FORWARD);

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> lostService.findAll(
                            ItemFilterInput.EMPTY, ItemSort.DATE_DESC, subrange(keyset, 5)))
                    .withMessageContaining("курсор");
        }
    }

    private ThingCategory categoryByKey(String key) {
        return categoryRepository.findAll().stream()
                .filter(category -> key.equals(category.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private void persistLost(String title, LocalDate date, ThingCategory category) {
        entityManager.persist(item(new LostItem(), title, date, category, storedPlace(),
                entityManager.persist(contact())));
    }

    private Place storedPlace() {
        return placeRepository.findById("ChIJplaceId")
                .orElseGet(() -> entityManager.persist(
                        place("ChIJplaceId", "Площа Ринок", 49.8419, 24.0315)));
    }

    private static ScrollSubrange subrange(ScrollPosition position, Integer count) {
        return ScrollSubrange.create(position, count, true);
    }

    private ItemInput input(MoneyInput compensation, String countryCode) {
        return input("keys.png", documents.getId(), compensation, countryCode);
    }

    private static ItemInput input(String image, Long categoryId) {
        return input(image, categoryId, new MoneyInput(500, null), "UA");
    }

    private static ItemInput input(String image, Long categoryId, MoneyInput compensation, String countryCode) {
        return new ItemInput(
                "Ключі",
                "Звʼязка з брелоком",
                SEPTEMBER_1,
                compensation,
                image,
                categoryId,
                new PlaceInput("ChIJplaceId", "Площа Ринок", 49.8419, 24.0315, countryCode),
                new ItemContactInfoInput("+380671234567", "finder@example.com",
                        List.of(SocialMediaEnum.TELEGRAM)));
    }
}
