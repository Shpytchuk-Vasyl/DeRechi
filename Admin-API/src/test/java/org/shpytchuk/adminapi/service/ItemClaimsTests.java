package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.detail.Place;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.lost.LostItemClaim;
import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.entity.thing.ThingCategory;
import org.shpytchuk.adminapi.repository.AbstractRepositoryTests;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.adminapi.support.Fixtures;
import org.shpytchuk.adminapi.view.matching.ClaimStatus;
import org.shpytchuk.adminapi.view.matching.ClaimView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The claims shown in the lists and archives, read through the derived queries on the real schema. */
class ItemClaimsTests extends AbstractRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private LostItemClaimRepository claims;

    @Autowired
    private ContactInfoRepository contacts;

    @Autowired
    private TestEntityManager entityManager;

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
    void groupsTheClaimsOfLiveNoticesNewestFirstAndKeepsNoticesWithoutAny() {
        LostItem wallet = item(new LostItem());
        LostItem keys = item(new LostItem());
        LostItem quiet = item(new LostItem());
        claim(wallet, "+380501111111", NOW.minusSeconds(120));
        LostItemClaim newest = claim(wallet, "+380502222222", NOW);
        newest.setConfirmedAt(NOW);
        claim(keys, "+380503333333", NOW);
        entityManager.flush();
        entityManager.clear();

        Map<Long, List<ClaimView>> byItem = ItemClaims.ofLive(claims, contacts)
                .byItem(List.of(wallet.getId(), quiet.getId()));

        assertThat(byItem).containsOnlyKeys(wallet.getId(), quiet.getId());
        assertThat(byItem.get(wallet.getId())).extracting(ClaimView::phone)
                .containsExactly("+380502222222", "+380501111111");
        assertThat(byItem.get(wallet.getId()).getFirst().status()).isEqualTo(ClaimStatus.CONFIRMED);
        assertThat(byItem.get(wallet.getId()).getFirst().socialMedias()).containsExactly(SocialMediaEnum.VIBER);
        assertThat(byItem.get(quiet.getId())).isEmpty();
    }

    @Test
    void findsTheClaimsOfAnArchivedNoticeByItsHistoryCopy() {
        LostItemHistory archived = item(new LostItemHistory());
        LostItem live = item(new LostItem());
        claim(archived, "+380501111111", NOW);
        claim(live, "+380502222222", NOW);
        entityManager.flush();
        entityManager.clear();

        Map<Long, List<ClaimView>> byItem = ItemClaims.ofArchived(claims, contacts).byItem(List.of(archived.getId()));

        assertThat(byItem.get(archived.getId())).extracting(ClaimView::phone).containsExactly("+380501111111");
    }

    @Test
    void answersAnEmptyPageWithoutQuerying() {
        assertThat(ItemClaims.ofLive(claims, contacts).byItem(List.of())).isEmpty();
    }

    @Test
    void deletesOnlyTheClaimsOfTheNoticeTogetherWithTheirContacts() {
        LostItem wallet = item(new LostItem());
        LostItem keys = item(new LostItem());
        LostItemClaim gone = claim(wallet, "+380501111111", NOW);
        LostItemClaim kept = claim(keys, "+380502222222", NOW);
        Long goneContact = gone.getContactInfo().getId();
        entityManager.clear();

        ItemClaims.ofLive(claims, contacts).deleteOf(wallet.getId());
        entityManager.flush();

        assertThat(claims.findAll()).extracting(LostItemClaim::getId).containsExactly(kept.getId());
        assertThat(contacts.findById(goneContact)).isEmpty();
        assertThat(contacts.findById(kept.getContactInfo().getId())).isPresent();
    }

    private <T extends Thing> T item(T item) {
        ContactInfo author = entityManager.persist(Fixtures.contact("+380671234567", "owner@example.com"));
        return entityManager.persist(Fixtures.item(item, "Гаманець", wallets, rynok, author));
    }

    private LostItemClaim claim(Thing item, String phone, Instant createdAt) {
        ContactInfo claimant = entityManager.persist(
                Fixtures.contact(phone, phone.substring(1) + "@example.com", SocialMediaEnum.VIBER));
        LostItemClaim claim = entityManager.persist(Fixtures.claim(new LostItemClaim(), item, claimant, createdAt));
        entityManager.flush();
        return claim;
    }
}
