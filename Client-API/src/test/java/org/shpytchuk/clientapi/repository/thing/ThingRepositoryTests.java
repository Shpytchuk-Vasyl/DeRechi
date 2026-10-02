package org.shpytchuk.clientapi.repository.thing;

import org.shpytchuk.clientapi.repository.AbstractRepositoryTests;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.entity.thing.ThingCategory;
import org.shpytchuk.clientapi.input.ItemFilterInput;
import org.shpytchuk.clientapi.input.NearInput;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.specification.ThingSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

        assertThat(titlesMatching(searching("КЛЮЧІ")))
                .containsExactly("Ключі від авто");
    }

    @Test
    void searchFilterMatchesTitleOrDescription() {
        persistLost("Ключі від авто", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, rynok).setDescription("Шкіряний, всередині ключі");
        persistLost("Паспорт", SEPTEMBER_1, keys, rynok).setDescription("Синя обкладинка");
        entityManager.flush();

        assertThat(titlesMatching(searching("ключі")))
                .containsExactlyInAnyOrder("Ключі від авто", "Гаманець");
    }

    @Test
    void searchFilterAlsoMatchesThePlaceName() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, opera);
        entityManager.flush();

        assertThat(titlesMatching(searching("оперний")))
                .containsExactly("Гаманець");
    }

    @Test
    void searchFilterTreatsLikeWildcardsAsPlainCharacters() {
        persistLost("Знижка 50% на ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, rynok);
        entityManager.flush();

        assertThat(titlesMatching(searching("%"))).containsExactly("Знижка 50% на ключі");
        assertThat(titlesMatching(searching("_"))).isEmpty();
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
    void nearFilterKeepsOnlyItemsWithinTheRadius() {
        Place kyiv = entityManager.persist(place("ChIJkyiv", "Майдан Незалежності", 50.4501, 30.5234));
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, opera);
        persistLost("Паспорт", SEPTEMBER_1, keys, kyiv);
        entityManager.flush();

        assertThat(titlesMatching(nearRynok(50.0)))
                .containsExactlyInAnyOrder("Ключі", "Гаманець");
    }

    @Test
    void nearFilterRadiusIsInKilometres() {
        // Від Ринку до Оперного ~450 м
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Гаманець", SEPTEMBER_1, wallet, opera);
        entityManager.flush();

        assertThat(titlesMatching(nearRynok(0.1))).containsExactly("Ключі");
        assertThat(titlesMatching(nearRynok(1.0))).containsExactlyInAnyOrder("Ключі", "Гаманець");
    }

    @Test
    void dateRangeFilterIsInclusiveOnBothEnds() {
        persistLost("Раніше", SEPTEMBER_1.minusDays(1), keys, rynok);
        persistLost("Початок", SEPTEMBER_1, keys, rynok);
        persistLost("Кінець", SEPTEMBER_1.plusDays(2), keys, rynok);
        persistLost("Пізніше", SEPTEMBER_1.plusDays(3), keys, rynok);
        entityManager.flush();

        assertThat(titlesMatching(new ItemFilterInput(
                null, null, SEPTEMBER_1, SEPTEMBER_1.plusDays(2), null)))
                .containsExactlyInAnyOrder("Початок", "Кінець");
    }

    @Test
    void filtersCombineWithAnd() {
        persistLost("Ключі", SEPTEMBER_1, keys, rynok);
        persistLost("Ключі", SEPTEMBER_1, wallet, rynok);
        persistLost("Ключі", SEPTEMBER_1.plusDays(10), keys, rynok);
        persistLost("Ключі", SEPTEMBER_1, keys, opera);
        entityManager.flush();

        Page<LostItem> page = lostItemRepository.findAll(
                ThingSpecifications.byFilter(new ItemFilterInput(
                        "ключ", keys.getId(), SEPTEMBER_1, SEPTEMBER_1, new NearInput(49.8419, 24.0315, 0.1))),
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

    private static ItemFilterInput searching(String search) {
        return new ItemFilterInput(search, null, null, null, null);
    }

    private static ItemFilterInput nearRynok(double radiusKm) {
        return new ItemFilterInput(null, null, null, null, new NearInput(49.8419, 24.0315, radiusKm));
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
