package org.shpytchuk.clientapi.repository;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.ContactInfo;
import org.shpytchuk.clientapi.entity.FoundItem;
import org.shpytchuk.clientapi.entity.LostItem;
import org.shpytchuk.clientapi.entity.Place;
import org.shpytchuk.clientapi.entity.Thing;
import org.shpytchuk.clientapi.entity.ThingCategory;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.specification.ThingSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.clientapi.support.Fixtures.contact;
import static org.shpytchuk.clientapi.support.Fixtures.item;
import static org.shpytchuk.clientapi.support.Fixtures.place;

class ThingRepositoryTests extends AbstractRepositoryTests {

    private static final LocalDate SEPTEMBER_1 = LocalDate.of(2026, 9, 1);

    @Autowired
    private LostItemRepository lostItemRepository;

    @Autowired
    private FoundItemRepository foundItemRepository;

    @Autowired
    private ThingCategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ThingCategory keys;
    private ThingCategory wallet;
    private Place rynok;
    private Place opera;

    @BeforeEach
    void seed() {
        keys = categoryRepository.findAll().stream()
                .filter(category -> "DOCUMENTS".equals(category.getKey()))
                .findFirst()
                .orElseThrow();
        wallet = categoryRepository.findAll().stream()
                .filter(category -> "WALLET".equals(category.getKey()))
                .findFirst()
                .orElseThrow();

        rynok = entityManager.persist(place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        opera = entityManager.persist(place("ChIJopera", "Оперний театр", 49.8440, 24.0263));
    }

    @Test
    void findWithDetailsByIdFetchesEveryAssociationInOneGo() {
        LostItem persisted = persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        entityManager.flush();
        entityManager.clear();

        Optional<LostItem> found = lostItemRepository.findWithDetailsById(persisted.getId());

        assertThat(found).isPresent();
        LostItem item = found.orElseThrow();
        assertThat(Hibernate.isInitialized(item.getCategory())).isTrue();
        assertThat(Hibernate.isInitialized(item.getPlace())).isTrue();
        assertThat(Hibernate.isInitialized(item.getInfo())).isTrue();
        assertThat(item.getPlace().getName()).isEqualTo("Площа Ринок");
    }

    @Test
    void findWithDetailsByIdReturnsEmptyForAnUnknownId() {
        assertThat(lostItemRepository.findWithDetailsById(-1L)).isEmpty();
    }

    @Test
    void searchFilterMatchesCaseInsensitiveSubstring() {
        persistLost("Ключі від авто", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, rynok);
        entityManager.flush();

        assertThat(titlesMatching(new ItemFilterInput("КЛЮЧІ", null, null, null, null)))
                .containsExactly("Ключі від авто");
    }

    @Test
    void categoryFilterNarrowsTheResult() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, rynok);
        entityManager.flush();

        assertThat(titlesMatching(new ItemFilterInput(null, wallet.getId(), null, null, null)))
                .containsExactly("Гаманець");
    }

    @Test
    void placeFilterUsesTheGooglePlaceId() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, opera);
        entityManager.flush();

        assertThat(titlesMatching(new ItemFilterInput(null, null, "ChIJopera", null, null)))
                .containsExactly("Гаманець");
    }

    @Test
    void dateRangeFilterIsInclusiveOnBothEnds() {
        persistLost("Раніше", SEPTEMBER_1.minusDays(1), keys, rynok);
        persistLost("Початок", SEPTEMBER_1, keys, rynok);
        persistLost("Кінець", SEPTEMBER_1.plusDays(2), keys, rynok);
        persistLost("Пізніше", SEPTEMBER_1.plusDays(3), keys, rynok);
        entityManager.flush();

        assertThat(titlesMatching(new ItemFilterInput(
                null, null, null, SEPTEMBER_1, SEPTEMBER_1.plusDays(2))))
                .containsExactlyInAnyOrder("Початок", "Кінець");
    }

    @Test
    void filtersCombineWithAnd() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Ключі", SEPTEMBER_1, wallet, rynok);
        persistLost("Ключі", SEPTEMBER_1.plusDays(10), keys, rynok);
        entityManager.flush();

        Page<LostItem> page = lostItemRepository.findAll(
                ThingSpecifications.byFilter(new ItemFilterInput(
                        "ключ", keys.getId(), "ChIJrynok", SEPTEMBER_1, SEPTEMBER_1)),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void emptyFilterReturnsEverything() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, opera);
        entityManager.flush();

        assertThat(titlesMatching(ItemFilterInput.EMPTY)).hasSize(2);
    }

    @Test
    void pagedQueryStillFetchesAssociationsEagerly() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        entityManager.flush();
        entityManager.clear();

        Page<LostItem> page = lostItemRepository.findAll(
                ThingSpecifications.byFilter(ItemFilterInput.EMPTY), PageRequest.of(0, 10));

        LostItem item = page.getContent().getFirst();
        assertThat(Hibernate.isInitialized(item.getPlace())).isTrue();
        assertThat(Hibernate.isInitialized(item.getInfo())).isTrue();
        assertThat(Hibernate.isInitialized(item.getCategory())).isTrue();
    }

    private Iterable<String> titlesMatching(ItemFilterInput filter) {
        return lostItemRepository
                .findAll(ThingSpecifications.byFilter(filter), PageRequest.of(0, 10))
                .getContent()
                .stream()
                .map(Thing::getTitle)
                .toList();
    }

    private LostItem persistLost(String title, LocalDate date, ThingCategory category, Place place) {
        ContactInfo info = entityManager.persist(contact());
        return entityManager.persist(item(new LostItem(), title, date, category, place, info));
    }
}
