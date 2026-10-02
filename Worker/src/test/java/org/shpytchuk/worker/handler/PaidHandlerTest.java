package org.shpytchuk.worker.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.event.NotificationRequestedEvent;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.support.Fixtures;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaidHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant PAID_AT = NOW.minusSeconds(60);

    private LostItemClaimRepository lostClaims;
    private FoundItemClaimRepository foundClaims;
    private RabbitTemplate rabbitTemplate;
    private PaidHandler lostHandler;
    private PaidHandler foundHandler;

    @BeforeEach
    void setUp() {
        lostClaims = mock(LostItemClaimRepository.class);
        foundClaims = mock(FoundItemClaimRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        ClaimRepositories repositories = new ClaimRepositories(lostClaims, foundClaims);
        ClaimNotifier notifier = new ClaimNotifier(rabbitTemplate, Fixtures.PROPERTIES, Fixtures.messages());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        lostHandler = new PaidHandler(ItemKind.LOST, repositories, notifier, clock);
        foundHandler = new PaidHandler(ItemKind.FOUND, repositories, notifier, clock);
    }

    @Test
    void routingKeysFollowTheKind() {
        assertThat(lostHandler.routingKey()).isEqualTo("item.lost.paid");
        assertThat(foundHandler.routingKey()).isEqualTo("item.found.paid");
    }

    @Test
    void sendsTheAuthorContactsToTheClaimantAndStampsTheClaim() {
        FoundItemClaim claim = paid(Fixtures.foundClaim("+380671234567"));
        when(foundClaims.findWithDetailsById(43L)).thenReturn(Optional.of(claim));

        foundHandler.handle(event(43L));

        NotificationRequestedEvent sent = published();
        assertThat(sent.phone()).isEqualTo("+12125550123");
        assertThat(sent.email()).isEqualTo("owner@example.com");
        assertThat(sent.message()).contains("+380 67 123 4567").doesNotContain("finder@example.com");
        assertThat(sent.deduplicationKey()).isEqualTo("claim:found:43:unlocked");
        assertThat(claim.getContactsSentAt()).isEqualTo(NOW);
        verify(foundClaims).save(claim);
        verify(lostClaims, never()).findWithDetailsById(any());
    }

    @Test
    void aReplayAfterTheContactsWereSentIsSilent() {
        LostItemClaim claim = paid(Fixtures.lostClaim("+380671234567"));
        claim.setContactsSentAt(PAID_AT);
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(claim));

        lostHandler.handle(event(42L));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(lostClaims, never()).save(any());
        assertThat(claim.getContactsSentAt()).isEqualTo(PAID_AT);
    }

    @Test
    void anArchivedNoticeGivesTheAuthorContactsFromItsHistoryCopy() {
        LostItemClaim claim = paid(Fixtures.lostClaim("+380671234567"));
        LostItemHistory history = new LostItemHistory();
        history.setId(7L);
        history.setTitle("Archived wallet");
        history.setInfo(Fixtures.contact("+48512345678", "archived-author@example.com"));
        claim.moveToArchive(history);
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(claim));

        lostHandler.handle(event(42L));

        NotificationRequestedEvent sent = published();
        assertThat(sent.subject()).contains("«Archived wallet»");
        assertThat(sent.message()).contains("+48 512 345 678").doesNotContain("archived-author@example.com");
        assertThat(sent.email()).isEqualTo("finder@example.com");
        assertThat(claim.getContactsSentAt()).isEqualTo(NOW);
    }

    @Test
    void aClaimThatIsNotPaidOrIsGoneSendsNothing() {
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(Fixtures.lostClaim("+380671234567")));
        when(lostClaims.findWithDetailsById(404L)).thenReturn(Optional.empty());

        lostHandler.handle(event(42L));
        lostHandler.handle(event(404L));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(lostClaims, never()).save(any());
    }

    private NotificationRequestedEvent published() {
        ArgumentCaptor<NotificationRequestedEvent> event = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("derechi.notifications"), eq(ClaimNotifier.UNLOCKED_ROUTING_KEY),
                event.capture());
        return event.getValue();
    }

    private static <C extends Claim> C paid(C claim) {
        claim.setPaidAt(PAID_AT);
        return claim;
    }

    private static ClaimEvent event(Long id) {
        ClaimEvent event = new ClaimEvent();
        event.setId(id);
        return event;
    }
}
