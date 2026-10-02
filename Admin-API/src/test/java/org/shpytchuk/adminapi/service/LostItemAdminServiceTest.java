package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.entity.lost.LostItemClaim;
import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.event.ArchiveRequestedEvent;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.detail.PlaceRepository;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemRepository;
import org.shpytchuk.adminapi.repository.matching.SimilarItemRepository;
import org.shpytchuk.adminapi.service.lost.LostItemAdminService;
import org.shpytchuk.adminapi.service.lost.LostItemHistoryAdminService;
import org.shpytchuk.adminapi.view.matching.ClaimStatus;
import org.shpytchuk.adminapi.view.matching.ClaimView;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LostItemAdminServiceTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");

    private LostItemRepository repository;
    private ContactInfoRepository contactInfoRepository;
    private SimilarItemRepository similarItemRepository;
    private LostItemClaimRepository claimRepository;
    private RabbitTemplate rabbitTemplate;
    private LostItemAdminService service;

    @BeforeEach
    void setUp() {
        repository = mock(LostItemRepository.class);
        contactInfoRepository = mock(ContactInfoRepository.class);
        similarItemRepository = mock(SimilarItemRepository.class);
        claimRepository = mock(LostItemClaimRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new LostItemAdminService(repository, mock(ThingCategoryRepository.class),
                mock(PlaceRepository.class), contactInfoRepository,
                new CountriesProperties(List.of("UA", "PL"), "UA"),
                similarItemRepository, claimRepository, rabbitTemplate, new ArchiveProperties("derechi.items"));
    }

    @Test
    void archiveAsksTheWorkerAndTouchesNoRows() {
        LostItem item = item(7L);
        when(repository.findWithDetailsById(7L)).thenReturn(Optional.of(item));

        service.archive(7L, "admin@derechi.local");

        ArgumentCaptor<ArchiveRequestedEvent> event = ArgumentCaptor.forClass(ArchiveRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("derechi.items"), eq("item.lost.archive"), event.capture());
        assertThat(event.getValue().id()).isEqualTo(7L);
        assertThat(event.getValue().actor()).isEqualTo("admin@derechi.local");
        verify(repository, never()).delete(any(LostItem.class));
        verify(similarItemRepository, never()).deleteByLostItemId(any());
        verify(claimRepository, never()).deleteAll(anyIterable());
    }

    @Test
    void archiveOfAMissingNoticeIsNotFoundAndPublishesNothing() {
        when(repository.findWithDetailsById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(7L, "admin@derechi.local")).isInstanceOf(NotFoundException.class);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void deleteDeletesTheClaimsThenTheirContactsBeforeTheItem() {
        LostItem item = item(7L);
        LostItemClaim claim = claim(item, "+380671111111", null, null, null);
        when(repository.findWithDetailsById(7L)).thenReturn(Optional.of(item));
        when(claimRepository.findByItemId(7L)).thenReturn(List.of(claim));

        service.delete(7L);

        InOrder order = inOrder(similarItemRepository, claimRepository, contactInfoRepository, repository);
        order.verify(similarItemRepository).deleteByLostItemId(7L);
        order.verify(claimRepository).deleteAll(List.of(claim));
        order.verify(contactInfoRepository).deleteAll(List.of(claim.getContactInfo()));
        order.verify(repository).delete(item);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void deleteWithoutClaimsTouchesNoContacts() {
        LostItem item = item(7L);
        when(repository.findWithDetailsById(7L)).thenReturn(Optional.of(item));
        when(claimRepository.findByItemId(7L)).thenReturn(List.of());

        service.delete(7L);

        verify(claimRepository, never()).deleteAll(anyIterable());
        verify(contactInfoRepository, never()).deleteAll(anyIterable());
        verify(repository).delete(item);
    }

    @Test
    void groupsClaimsByItemAndDerivesTheirStatus() {
        LostItem seven = item(7L);
        LostItem eight = item(8L);
        LostItemClaim confirmed = claim(seven, "+380671111111", CREATED.plusSeconds(60), CREATED, null);
        LostItemClaim reminded = claim(seven, "+380672222222", null, null, CREATED);
        LostItemClaim fresh = claim(eight, "+48501234567", null, null, null);
        when(claimRepository.findByItemIdInOrderByCreatedAtDescIdDesc(List.of(7L, 8L, 9L))).thenReturn(List.of(confirmed, reminded, fresh));

        Map<Long, List<ClaimView>> claims = service.claims(List.of(7L, 8L, 9L));

        assertThat(claims).containsOnlyKeys(7L, 8L, 9L);
        assertThat(claims.get(7L)).extracting(ClaimView::phone, ClaimView::status).containsExactly(
                tuple("+380671111111", ClaimStatus.CONFIRMED),
                tuple("+380672222222", ClaimStatus.REMINDED));
        assertThat(claims.get(8L)).singleElement().satisfies(view -> {
            assertThat(view.status()).isEqualTo(ClaimStatus.NEW);
            assertThat(view.email()).isEqualTo("claimant@example.com");
            assertThat(view.socialMedias()).containsExactly(SocialMediaEnum.VIBER);
            assertThat(view.createdAt()).isEqualTo(CREATED);
        });
        assertThat(claims.get(9L)).as("a notice nobody responded to is still a key").isEmpty();
    }

    @Test
    void doesNotQueryClaimsForAnEmptyPage() {
        assertThat(service.claims(List.of())).isEmpty();

        verify(claimRepository, never()).findByItemIdInOrderByCreatedAtDescIdDesc(any());
    }

    @Test
    void theArchiveListsClaimsByTheirHistoryCopy() {
        LostItemHistory history = new LostItemHistory();
        history.setId(500L);
        LostItemClaim moved = claim(null, "+380671111111", CREATED.plusSeconds(60), CREATED, null);
        moved.setArchivedItem(history);
        when(claimRepository.findByArchivedItemIdInOrderByCreatedAtDescIdDesc(List.of(500L))).thenReturn(List.of(moved));
        when(claimRepository.findByArchivedItemId(500L)).thenReturn(List.of(moved));
        LostItemHistoryRepository historyRepository = mock(LostItemHistoryRepository.class);
        when(historyRepository.findWithDetailsById(500L)).thenReturn(Optional.of(history));
        LostItemHistoryAdminService archive = new LostItemHistoryAdminService(
                historyRepository, mock(ThingCategoryRepository.class),
                mock(PlaceRepository.class), contactInfoRepository,
                new CountriesProperties(List.of("UA"), "UA"), claimRepository);

        Map<Long, List<ClaimView>> claims = archive.claims(List.of(500L));

        assertThat(claims.get(500L)).extracting(ClaimView::phone, ClaimView::status)
                .containsExactly(tuple("+380671111111", ClaimStatus.CONFIRMED));
        assertThatThrownBy(() -> archive.archive(500L, "admin@derechi.local"))
                .isInstanceOf(UnsupportedOperationException.class);

        archive.delete(500L);

        InOrder order = inOrder(claimRepository, contactInfoRepository, historyRepository);
        order.verify(claimRepository).deleteAll(List.of(moved));
        order.verify(contactInfoRepository).deleteAll(List.of(moved.getContactInfo()));
        order.verify(historyRepository).delete(history);
    }

    private static LostItem item(Long id) {
        LostItem item = new LostItem();
        item.setId(id);
        item.setTitle("Рюкзак");
        return item;
    }

    private static LostItemClaim claim(LostItem item, String phone, Instant confirmedAt,
                                       Instant authorRemindedAt, Instant claimantRemindedAt) {
        ContactInfo info = new ContactInfo();
        info.setPhone(phone);
        info.setEmail("claimant@example.com");
        info.setSocialMedias(new SocialMediaEnum[]{SocialMediaEnum.VIBER});

        LostItemClaim claim = new LostItemClaim();
        claim.setItem(item);
        claim.setContactInfo(info);
        claim.setToken("00000000-0000-0000-0000-000000000000");
        claim.setCreatedAt(CREATED);
        claim.setConfirmedAt(confirmedAt);
        claim.setAuthorRemindedAt(authorRemindedAt);
        claim.setClaimantRemindedAt(claimantRemindedAt);
        return claim;
    }
}
