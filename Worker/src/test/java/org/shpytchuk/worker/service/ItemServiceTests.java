package org.shpytchuk.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Window;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ItemServiceTests extends AbstractRepositoryTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private static final double LAT = 49.8419;
    private static final double LON = 24.0315;

    private static final LanguageResolver LANGUAGES = new LanguageResolver();

    @Autowired
    private LostItemRepository repository;

    private ItemService<LostItem> service;
    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        service = new ItemService<>(repository, LANGUAGES);
        wallets = category("WALLET");
        rynok = place("ChIJrynok", LAT, LON);
    }

    @Test
    void findsOnlyTheSameCategoryWithinThreeDaysAndTwentyKilometres() {
        LostItem match = lost("Wallet", DATE.plusDays(2), wallets, rynok);
        LostItem edgeOfTheWindow = lost("Wallet", DATE.minusDays(3), wallets, rynok);
        LostItem nearby = lost("Wallet", DATE, wallets, place("ChIJsykhiv", 49.8050, 24.0500));
        lost("Wallet", DATE, category("KEYS"), rynok);
        lost("Wallet", DATE.plusDays(4), wallets, rynok);
        lost("Wallet", DATE.minusDays(4), wallets, rynok);
        lost("Wallet", DATE, wallets, place("ChIJzhovkva", 50.0730, 23.9730));
        lost("Wallet", DATE, wallets, place("ChIJkyiv", 50.4501, 30.5234));

        Window<LostItem> found = service.findAllMostSuitable(event("Wallet"));

        assertThat(found.getContent()).extracting(Thing::getId)
                .containsExactlyInAnyOrder(match.getId(), edgeOfTheWindow.getId(), nearby.getId());
    }

    @Test
    void putsTheMostRelevantFirstAndGivesEachCandidateItsOwnRank() {
        LostItem byDescription = lost("Purse", "black leather, two zips");
        LostItem byWholeTitle = lost("Black leather wallet", null);

        List<LostItem> found = service.findAllMostSuitable(event("Black leather wallet")).getContent();

        assertThat(found).extracting(Thing::getId).containsExactly(byWholeTitle.getId(), byDescription.getId());
        for (LostItem candidate : found) {
            assertThat(candidate.getOrderMatch()).as("rank of %s", candidate.getTitle())
                    .isCloseTo(rank("english", candidate.getId(), "black | leather | wallet"), within(1e-6));
        }
        assertThat(found.get(0).getOrderMatch()).isGreaterThan(found.get(1).getOrderMatch());
    }

    @Test
    void skipsCandidatesThatShareNoWord() {
        LostItem wallet = lost("Black leather wallet", null);
        lost("Umbrella", null);

        assertThat(service.findAllMostSuitable(event("Black leather wallet")).getContent())
                .extracting(Thing::getId).containsExactly(wallet.getId());
    }

    @Test
    void needsTwoSharedWordsWhenTheTitleHasMore() {
        LostItem passport = lost("Знайдено паспорт на ім'я Марія", null);
        lost("Знайдено леокарт десь біля форуму", null);
        lost("Знайдено студентський квиток, Марія", null);

        assertThat(service.findAllMostSuitable(event("паспорт Аля Марія")).getContent())
                .extracting(Thing::getId).containsExactly(passport.getId());
    }

    @Test
    void aTypoLeavesOnlyOneSharedWord() {
        lost("Знайдено паспорт на ім'я Марія", null);

        assertThat(service.findAllMostSuitable(event("паспотр Аля Марія")).getContent()).isEmpty();
    }

    @Test
    void aOneWordTitleNeedsOneSharedWord() {
        LostItem phone = lost("Знайдено телефон Samsung", null);

        assertThat(service.findAllMostSuitable(event("Телефон")).getContent())
                .extracting(Thing::getId).containsExactly(phone.getId());
    }

    @Test
    void aRepeatedWordCountsOnce() {
        lost("Чорний пес", null);

        assertThat(service.findAllMostSuitable(event("Чорний телефон, чорний чохол")).getContent()).isEmpty();
    }

    @Test
    void putsTheFullNameAboveTheFirstNameAlone() {
        LostItem firstNameOnly = lost("Знайдено Леокарту на ім'я Максим Петренко", null);
        LostItem fullName = lost("Знайдено Леокарту на ім'я Столяр Максим", null);

        List<LostItem> found = service.findAllMostSuitable(event("Загублено Леокарту Столяр Максим")).getContent();

        assertThat(found).extracting(Thing::getId).containsExactly(fullName.getId(), firstNameOnly.getId());
        assertThat(found.get(0).getOrderMatch()).isGreaterThan(found.get(1).getOrderMatch());
    }

    @Test
    void ranksAUkrainianTitleWithTheUkrainianConfiguration() {
        LostItem umbrella = lost("Парасолька", null);
        LostItem wallet = lost("Чорний шкіряний гаманець", null);

        List<LostItem> found = service.findAllMostSuitable(event("Чорний шкіряний гаманець")).getContent();

        assertThat(found).extracting(Thing::getId).containsExactly(wallet.getId()).doesNotContain(umbrella.getId());
        assertThat(found.getFirst().getOrderMatch())
                .isCloseTo(rank("ukrainian", wallet.getId(), "чорний | шкіряний | гаманець"), within(1e-6))
                .isPositive();
    }

    @Test
    void ranksAPolishTitleWithThePolishConfiguration() {
        LostItem umbrella = lost("Parasolka", null);
        LostItem wallet = lost("Czarny skórzany portfel z dokumentami", null);

        List<LostItem> found = service.findAllMostSuitable(event("Czarny skórzany portfel z dokumentami")).getContent();

        assertThat(found).extracting(Thing::getId).containsExactly(wallet.getId()).doesNotContain(umbrella.getId());
        assertThat(found.getFirst().getOrderMatch())
                .isCloseTo(rank("polish", wallet.getId(), "czarny | skórzany | portfel | z | dokumentami"),
                        within(1e-6))
                .isPositive();
    }

    @Test
    void keepsAtMostFiveCandidates() {
        for (int i = 0; i < 7; i++) {
            lost("Wallet " + i, null);
        }

        Window<LostItem> found = service.findAllMostSuitable(event("Wallet"));

        assertThat(found.getContent()).hasSize(5);
        assertThat(found.hasNext()).isTrue();
    }

    @Test
    void findsNothingWhenTheTitleIsBlank() {
        lost("Wallet", null);

        assertThat(service.findAllMostSuitable(event("  ")).getContent()).isEmpty();
    }

    @Test
    void findsNothingWhenNothingIsClose() {
        lost("Wallet", DATE, wallets, place("ChIJkyiv", 50.4501, 30.5234));

        assertThat(service.findAllMostSuitable(event("Wallet")).getContent()).isEmpty();
    }

    private ItemCreatedEvent event(String title) {
        return Fixtures.created(1000L, title, DATE, wallets.getId(), LAT, LON);
    }

    private LostItem lost(String title, String description) {
        return item(new LostItem(), title, description, DATE, wallets, rynok);
    }

    private LostItem lost(String title, LocalDate date, ThingCategory category, Place place) {
        return item(new LostItem(), title, null, date, category, place);
    }

    /** The rank computed independently, straight from PostgreSQL. */
    private double rank(String regconfig, Long id, String anyWord) {
        Double rank = jdbc.queryForObject("""
                SELECT ts_rank_cd(to_tsvector(CAST(? AS regconfig), title || ' ' || coalesce(description, '')),
                                  to_tsquery(CAST(? AS regconfig), ?))
                FROM lost_item WHERE id = ?
                """, Double.class, regconfig, regconfig, anyWord, id);
        return rank == null ? 0.0 : rank;
    }
}
