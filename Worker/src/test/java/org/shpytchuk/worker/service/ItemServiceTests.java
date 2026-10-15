package org.shpytchuk.worker.service;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.AbstractRepositoryTests;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ItemServiceTests extends AbstractRepositoryTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);
    private static final double LAT = 49.8419;
    private static final double LON = 24.0315;

    private static final LanguageResolver LANGUAGES = new LanguageResolver();

    private ItemService<LostItem> service;
    private ThingCategory wallets;
    private Place rynok;

    @BeforeEach
    void setUp() {
        service = new ItemService<>(entityManager.getEntityManager(), LostItem.class, LANGUAGES);
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

        List<MatchCandidate> found = service.findMostSuitable(event("Wallet"));

        assertThat(found).extracting(MatchCandidate::id)
                .containsExactlyInAnyOrder(match.getId(), edgeOfTheWindow.getId(), nearby.getId());
    }

    @Test
    void putsTheMostRelevantFirstAndGivesEachCandidateItsOwnRank() {
        LostItem byDescription = lost("Purse", "black leather, two zips");
        LostItem byWholeTitle = lost("Black leather wallet", null);

        List<MatchCandidate> found = service.findMostSuitable(event("Black leather wallet"));

        assertThat(found).extracting(MatchCandidate::id).containsExactly(byWholeTitle.getId(), byDescription.getId());
        for (MatchCandidate candidate : found) {
            assertThat(candidate.rank()).as("rank of item %d", candidate.id())
                    .isCloseTo(rank("english", candidate.id(), "black | leather | wallet"), within(1e-6));
        }
        assertThat(found.get(0).rank()).isGreaterThan(found.get(1).rank());
    }

    @Test
    void skipsCandidatesThatShareNoWord() {
        LostItem wallet = lost("Black leather wallet", null);
        lost("Umbrella", null);

        assertThat(service.findMostSuitable(event("Black leather wallet")))
                .extracting(MatchCandidate::id).containsExactly(wallet.getId());
    }

    @Test
    void needsTwoSharedWordsWhenTheTitleHasMore() {
        LostItem passport = lost("Знайдено паспорт на ім'я Марія", null);
        lost("Знайдено леокарт десь біля форуму", null);
        lost("Знайдено студентський квиток, Марія", null);

        assertThat(service.findMostSuitable(event("паспорт Аля Марія")))
                .extracting(MatchCandidate::id).containsExactly(passport.getId());
    }

    @Test
    void aTypoLeavesOnlyOneSharedWord() {
        lost("Знайдено паспорт на ім'я Марія", null);

        assertThat(service.findMostSuitable(event("паспотр Аля Марія"))).isEmpty();
    }

    @Test
    void aOneWordTitleNeedsOneSharedWord() {
        LostItem phone = lost("Знайдено телефон Samsung", null);

        assertThat(service.findMostSuitable(event("Телефон")))
                .extracting(MatchCandidate::id).containsExactly(phone.getId());
    }

    @Test
    void aRepeatedWordCountsOnce() {
        lost("Чорний пес", null);

        assertThat(service.findMostSuitable(event("Чорний телефон, чорний чохол"))).isEmpty();
    }

    @Test
    void putsTheFullNameAboveTheFirstNameAlone() {
        LostItem firstNameOnly = lost("Знайдено Леокарту на ім'я Максим Петренко", null);
        LostItem fullName = lost("Знайдено Леокарту на ім'я Столяр Максим", null);

        List<MatchCandidate> found = service.findMostSuitable(event("Загублено Леокарту Столяр Максим"));

        assertThat(found).extracting(MatchCandidate::id).containsExactly(fullName.getId(), firstNameOnly.getId());
        assertThat(found.get(0).rank()).isGreaterThan(found.get(1).rank());
    }

    @Test
    void ranksAUkrainianTitleWithTheUkrainianConfiguration() {
        LostItem umbrella = lost("Парасолька", null);
        LostItem wallet = lost("Чорний шкіряний гаманець", null);

        List<MatchCandidate> found = service.findMostSuitable(event("Чорний шкіряний гаманець"));

        assertThat(found).extracting(MatchCandidate::id).containsExactly(wallet.getId()).doesNotContain(umbrella.getId());
        assertThat(found.getFirst().rank())
                .isCloseTo(rank("ukrainian", wallet.getId(), "чорний | шкіряний | гаманець"), within(1e-6))
                .isPositive();
    }

    @Test
    void ranksAPolishTitleWithThePolishConfiguration() {
        LostItem umbrella = lost("Parasolka", null);
        LostItem wallet = lost("Czarny skórzany portfel z dokumentami", null);

        List<MatchCandidate> found = service.findMostSuitable(event("Czarny skórzany portfel z dokumentami"));

        assertThat(found).extracting(MatchCandidate::id).containsExactly(wallet.getId()).doesNotContain(umbrella.getId());
        assertThat(found.getFirst().rank())
                .isCloseTo(rank("polish", wallet.getId(), "czarny | skórzany | portfel | z | dokumentami"),
                        within(1e-6))
                .isPositive();
    }

    @Test
    void keepsAtMostFiveCandidates() {
        for (int i = 0; i < 7; i++) {
            lost("Wallet " + i, null);
        }

        assertThat(service.findMostSuitable(event("Wallet"))).hasSize(5);
    }

    @Test
    void readsTheCandidatesAndTheirRanksInOneQuery() {
        for (int i = 0; i < 7; i++) {
            lost("Wallet " + i, null);
        }
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        assertThat(service.findMostSuitable(event("Wallet"))).hasSize(5);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void findsNothingWhenTheTitleIsBlank() {
        lost("Wallet", null);

        assertThat(service.findMostSuitable(event("  "))).isEmpty();
    }

    @Test
    void findsNothingWhenNothingIsClose() {
        lost("Wallet", DATE, wallets, place("ChIJkyiv", 50.4501, 30.5234));

        assertThat(service.findMostSuitable(event("Wallet"))).isEmpty();
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
