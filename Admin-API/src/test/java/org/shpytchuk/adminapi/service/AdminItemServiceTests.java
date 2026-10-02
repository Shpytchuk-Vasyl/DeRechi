package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.config.CountriesConfig;
import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.entity.found.FoundItemClaim;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.lost.LostItemClaim;
import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.repository.AbstractRepositoryTests;
import org.shpytchuk.adminapi.service.found.FoundItemAdminService;
import org.shpytchuk.adminapi.service.lost.LostItemAdminService;
import org.shpytchuk.adminapi.service.lost.LostItemHistoryAdminService;
import org.shpytchuk.adminapi.support.Fixtures;
import org.shpytchuk.adminapi.view.detail.ItemView;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Create, update, list and delete through the real services, on the real schema and its foreign keys. */
@Import({LostItemAdminService.class, FoundItemAdminService.class, LostItemHistoryAdminService.class,
        CountriesConfig.class, AdminItemServiceTests.Properties.class})
class AdminItemServiceTests extends AbstractRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private LostItemAdminService lostItems;

    @Autowired
    private FoundItemAdminService foundItems;

    @Autowired
    private LostItemHistoryAdminService lostHistory;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private ThingCategory wallets;
    private ThingCategory keys;

    @BeforeEach
    void setUp() {
        wallets = category("WALLET");
        keys = category("KEYS");
    }

    @Test
    void createsTheItemWithItsOwnContactAndPlace() {
        Long id = lostItems.create(Fixtures.form("Гаманець", wallets.getId(), "ChIJrynok", "ua")).getId();
        entityManager.flush();
        entityManager.clear();

        LostItem saved = entityManager.find(LostItem.class, id);
        assertThat(saved.getTitle()).isEqualTo("Гаманець");
        assertThat(saved.getDescription()).as("a blank description is stored as null").isNull();
        assertThat(saved.getCompensation()).isEqualTo(500);
        assertThat(saved.getCurrency()).as("from the country of the place").isEqualTo("UAH");
        assertThat(saved.getCategory().getId()).isEqualTo(wallets.getId());
        assertThat(saved.getPlace().getName()).isEqualTo("Площа Ринок");
        assertThat(saved.getPlace().getCountryCode()).isEqualTo("UA");
        assertThat(saved.getPlace().getCoordinate().getY()).isEqualTo(49.8419);
        assertThat(saved.getInfo().getPhone()).isEqualTo("+380671234567");
        assertThat(saved.getInfo().getSocialMedias()).containsExactly(SocialMediaEnum.TELEGRAM);
    }

    @Test
    void takesTheCurrencyOfThePlaceCountryUnlessTheFormNamesOne() {
        Long byCountry = lostItems.create(Fixtures.form("Гаманець", wallets.getId(), "ChIJwarsaw", "PL")).getId();
        ItemForm explicit = Fixtures.form("Гаманець", wallets.getId(), "ChIJberlin", "DE");
        explicit.setCurrency("pln");
        Long byForm = lostItems.create(explicit).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(LostItem.class, byCountry).getCurrency()).isEqualTo("PLN");
        assertThat(entityManager.find(LostItem.class, byForm).getCurrency()).isEqualTo("PLN");
    }

    @Test
    void refusesAnUnsupportedCountryOrCurrencyEvenPastTheFormValidator() {
        ItemForm currency = Fixtures.form("Гаманець", wallets.getId(), "ChIJrynok", "UA");
        currency.setCurrency("USD");

        assertThatThrownBy(() -> lostItems.create(Fixtures.form("Гаманець", wallets.getId(), "ChIJnyc", "US")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("US");
        assertThatThrownBy(() -> lostItems.create(currency))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("USD");
        assertThat(count("lost_item")).isZero();
    }

    @Test
    void refusesAnUnknownCategory() {
        assertThatThrownBy(() -> lostItems.create(Fixtures.form("Гаманець", 404L, "ChIJrynok", "UA")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reusesAPlaceByItsGooglePlaceIdAndKeepsTheLatestName() {
        lostItems.create(Fixtures.form("Гаманець", wallets.getId(), "ChIJrynok", "UA"));
        ItemForm renamed = Fixtures.form("Ключі", keys.getId(), "ChIJrynok", "UA");
        renamed.setPlaceName("Ринок, Львів");
        foundItems.create(renamed);
        entityManager.flush();

        assertThat(count("place")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM place", String.class)).isEqualTo("Ринок, Львів");
    }

    @Test
    void updatesTheItemAndItsContactInPlace() {
        Long id = lostItems.create(Fixtures.form("Гаманець", wallets.getId(), "ChIJrynok", "UA")).getId();
        entityManager.flush();
        entityManager.clear();

        ItemForm form = lostItems.form(id);
        form.setTitle("Чорний гаманець");
        form.setPhone("+48501234567");
        form.setSocialMedias(List.of());
        lostItems.update(id, form);
        entityManager.flush();
        entityManager.clear();

        LostItem updated = entityManager.find(LostItem.class, id);
        assertThat(updated.getTitle()).isEqualTo("Чорний гаманець");
        assertThat(updated.getInfo().getPhone()).isEqualTo("+48501234567");
        assertThat(updated.getInfo().getSocialMedias()).as("an empty choice is stored as null").isNull();
        assertThat(count("contact_info")).as("the author's contact is edited, not duplicated").isEqualTo(1);
    }

    @Test
    void listsWhatTheFilterMatchesInTheRequestedOrder() {
        Place rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        Place opera = entityManager.persist(Fixtures.place("ChIJopera", "Оперний театр", 49.8440, 24.0262));
        LostItem wallet = lost("Шкіряний гаманець", wallets, rynok, LocalDate.of(2026, 9, 10));
        LostItem byPlace = lost("Ключі від авто", keys, opera, LocalDate.of(2026, 9, 12));
        lost("Парасолька", keys, rynok, LocalDate.of(2026, 8, 1));

        assertThat(ids(new ItemFilter("ГАМАНЕЦЬ", null, null, null), Sort.unsorted())).containsExactly(wallet.getId());
        assertThat(ids(new ItemFilter("оперний", null, null, null), Sort.unsorted())).containsExactly(byPlace.getId());
        assertThat(ids(new ItemFilter(null, keys.getId(), LocalDate.of(2026, 9, 1), null), Sort.unsorted()))
                .containsExactly(byPlace.getId());
        assertThat(ids(new ItemFilter(null, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Sort.by("title")))
                .containsExactly(byPlace.getId(), wallet.getId());
    }

    @Test
    void ignoresASortOnAFieldOutsideTheWhitelist() {
        Place rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        LostItem first = lost("Б", wallets, rynok, LocalDate.of(2026, 9, 10));
        LostItem second = lost("А", wallets, rynok, LocalDate.of(2026, 9, 10));

        assertThat(ids(ItemFilter.empty(), Sort.by("info.phone"))).as("newest first instead")
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void deletesTheMatchesTheClaimsAndTheClaimantContactsBeforeTheItem() {
        Place rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        LostItem deleted = lost("Гаманець", wallets, rynok, LocalDate.of(2026, 9, 10));
        LostItem kept = lost("Ключі", keys, rynok, LocalDate.of(2026, 9, 10));
        FoundItem candidate = entityManager.persist(Fixtures.item(new FoundItem(), "Гаманець", wallets, rynok,
                entityManager.persist(Fixtures.contact("+380501112233", "finder@example.com"))));
        entityManager.flush();
        match(candidate, deleted);
        match(candidate, kept);
        claim(new LostItemClaim(), deleted, "+380501111111");
        claim(new LostItemClaim(), deleted, "+380502222222");
        LostItemClaim other = claim(new LostItemClaim(), kept, "+380503333333");
        long contactsBefore = count("contact_info");

        lostItems.delete(deleted.getId());
        entityManager.flush();

        assertThat(count("lost_item")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM lost_item_claim", Long.class)).containsExactly(other.getId());
        assertThat(count("contact_info")).as("both claimants' contacts go with their claims").isEqualTo(contactsBefore - 2);
        assertThat(jdbc.queryForList("SELECT lost_item_id FROM similar_item", Long.class)).containsExactly(kept.getId());
    }

    @Test
    void deletesAFoundItemWithItsMatchesAndClaims() {
        Place rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        FoundItem found = entityManager.persist(Fixtures.item(new FoundItem(), "Гаманець", wallets, rynok,
                entityManager.persist(Fixtures.contact("+380501112233", "finder@example.com"))));
        LostItem candidate = lost("Гаманець", wallets, rynok, LocalDate.of(2026, 9, 10));
        entityManager.flush();
        match(found, candidate);
        claim(new FoundItemClaim(), found, "+380501111111");

        foundItems.delete(found.getId());
        entityManager.flush();

        assertThat(count("found_item")).isZero();
        assertThat(count("found_item_claim")).isZero();
        assertThat(count("similar_item")).isZero();
        assertThat(count("lost_item")).isEqualTo(1);
    }

    @Test
    void deletesAnArchivedItemWithTheClaimsThatFollowedIt() {
        Place rynok = entityManager.persist(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        LostItemHistory archived = entityManager.persist(Fixtures.item(new LostItemHistory(), "Гаманець", wallets, rynok,
                entityManager.persist(Fixtures.contact("+380501112233", "owner@example.com"))));
        LostItem live = lost("Ключі", keys, rynok, LocalDate.of(2026, 9, 10));
        claim(new LostItemClaim(), archived, "+380501111111");
        claim(new LostItemClaim(), live, "+380502222222");

        lostHistory.delete(archived.getId());
        entityManager.flush();

        assertThat(count("lost_item_history")).isZero();
        assertThat(jdbc.queryForList("SELECT item_id FROM lost_item_claim", Long.class)).containsExactly(live.getId());
    }

    @Test
    void reportsAMissingItemAsNotFound() {
        assertThatThrownBy(() -> lostItems.delete(404L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> lostItems.form(404L)).isInstanceOf(NotFoundException.class);
    }

    private List<Long> ids(ItemFilter filter, Sort sort) {
        return lostItems.page(PageRequest.of(0, 20, sort), filter).map(ItemView::id).getContent();
    }

    private LostItem lost(String title, ThingCategory category, Place place, LocalDate date) {
        ContactInfo author = entityManager.persist(Fixtures.contact("+380671234567", "owner@example.com"));
        LostItem item = Fixtures.item(new LostItem(), title, category, place, author);
        item.setDate(date);
        LostItem saved = entityManager.persist(item);
        entityManager.flush();
        return saved;
    }

    private <C extends org.shpytchuk.adminapi.entity.matching.Claim> C claim(C claim, Thing item, String phone) {
        ContactInfo claimant = entityManager.persist(Fixtures.contact(phone, phone.substring(1) + "@example.com"));
        C saved = entityManager.persist(Fixtures.claim(claim, item, claimant, NOW));
        entityManager.flush();
        return saved;
    }

    private void match(FoundItem found, LostItem lost) {
        jdbc.update("INSERT INTO similar_item (found_item_id, lost_item_id, match_order) VALUES (?, ?, 0.5)",
                found.getId(), lost.getId());
    }

    private ThingCategory category(String key) {
        return entityManager.getEntityManager()
                .createQuery("select c from ThingCategory c where c.key = :key", ThingCategory.class)
                .setParameter("key", key)
                .getSingleResult();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @TestConfiguration
    @EnableConfigurationProperties(ArchiveProperties.class)
    static class Properties {
    }
}
