package org.shpytchuk.adminapi.service.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.repository.AbstractRepositoryTests;
import org.shpytchuk.adminapi.support.Fixtures;
import org.shpytchuk.adminapi.view.matching.CandidateView;
import org.shpytchuk.adminapi.view.matching.MatchRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The match page loads at most {@link MatchService#PREVIEW_SIZE} candidates per lost item with one correlated
 * query and the totals with another; both are JPQL that only a real database can check.
 */
@Import(MatchService.class)
class MatchServiceTests extends AbstractRepositoryTests {

    @Autowired
    private MatchService service;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        wallets = entityManager.getEntityManager()
                .createQuery("select c from ThingCategory c where c.key = 'WALLET'", ThingCategory.class)
                .getSingleResult();
        rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
    }

    @Test
    void previewsTheBestCandidatesOfEachLostItemAndCountsTheRest() {
        LostItem wallet = item(new LostItem(), "Гаманець");
        LostItem keys = item(new LostItem(), "Ключі");
        LostItem quiet = item(new LostItem(), "Парасолька");
        FoundItem first = item(new FoundItem(), "a");
        FoundItem second = item(new FoundItem(), "b");
        FoundItem tiedLowerId = item(new FoundItem(), "c");
        FoundItem tiedHigherId = item(new FoundItem(), "d");
        FoundItem worst = item(new FoundItem(), "e");
        match(worst, wallet, 0.01);
        match(tiedHigherId, wallet, 0.2);
        match(first, wallet, 0.9);
        match(tiedLowerId, wallet, 0.2);
        match(second, wallet, 0.5);
        match(worst, keys, 0.3);

        Map<Long, MatchRow> rows = page();

        assertThat(rows.get(wallet.getId()).candidates()).extracting(candidate -> candidate.found().id())
                .containsExactly(first.getId(), second.getId(), tiedLowerId.getId());
        assertThat(rows.get(wallet.getId()).totalCandidates()).isEqualTo(5);
        assertThat(rows.get(wallet.getId()).hiddenCount()).isEqualTo(2);
        assertThat(rows.get(keys.getId()).candidates()).extracting(candidate -> candidate.found().id())
                .containsExactly(worst.getId());
        assertThat(rows.get(keys.getId()).hasMore()).isFalse();
        assertThat(rows.get(quiet.getId()).candidates()).isEmpty();
        assertThat(rows.get(quiet.getId()).totalCandidates()).isZero();
    }

    @Test
    void filtersTheLostItemsLikeTheOtherLists() {
        LostItem wallet = item(new LostItem(), "Гаманець");
        item(new LostItem(), "Ключі");

        Page<MatchRow> page = service.page(PageRequest.of(0, 20), new ItemFilter("гаман", null, null, null));

        assertThat(page.getContent()).extracting(row -> row.lost().id()).containsExactly(wallet.getId());
    }

    @Test
    void loadsEveryCandidateOfOneLostItemOnRequest() {
        LostItem wallet = item(new LostItem(), "Гаманець");
        FoundItem[] found = new FoundItem[5];
        for (int i = 0; i < found.length; i++) {
            found[i] = item(new FoundItem(), "Гаманець " + i);
            match(found[i], wallet, i / 10.0);
        }
        entityManager.clear();

        MatchRow row = service.rowWithAllCandidates(wallet.getId());

        assertThat(row.candidates()).extracting(CandidateView::matchOrder).containsExactly(0.4, 0.3, 0.2, 0.1, 0.0);
        assertThat(row.candidates().getFirst().found().phone()).isEqualTo("+380671234567");
        assertThat(row.hasMore()).isFalse();
    }

    @Test
    void reportsAnUnknownLostItemAsNotFound() {
        assertThatThrownBy(() -> service.rowWithAllCandidates(404L)).isInstanceOf(NotFoundException.class);
    }

    private Map<Long, MatchRow> page() {
        entityManager.clear();
        return service.page(PageRequest.of(0, 20, Sort.by("id")), ItemFilter.empty()).getContent().stream()
                .collect(Collectors.toMap(row -> row.lost().id(), Function.identity()));
    }

    private <T extends Thing> T item(T item, String title) {
        ContactInfo author = entityManager.persist(Fixtures.contact("+380671234567", "owner@example.com"));
        T saved = entityManager.persist(Fixtures.item(item, title, wallets, rynok, author));
        entityManager.flush();
        return saved;
    }

    private void match(FoundItem found, LostItem lost, double order) {
        jdbc.update("INSERT INTO similar_item (found_item_id, lost_item_id, match_order) VALUES (?, ?, ?)",
                found.getId(), lost.getId(), order);
    }
}
