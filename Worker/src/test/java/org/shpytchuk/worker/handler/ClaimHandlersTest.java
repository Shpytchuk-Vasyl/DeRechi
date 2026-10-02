package org.shpytchuk.worker.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.found.FoundItemHistory;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.repository.found.FoundItemClaimRepository;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.support.Fixtures;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link ClaimedHandler} and {@link ReturnedHandler}: what a claim event does, and that a replay is harmless. */
class ClaimHandlersTest {

    private LostItemClaimRepository lostClaims;
    private FoundItemClaimRepository foundClaims;
    private ClaimNotifier notifier;
    private ItemArchiver archiver;
    private ClaimRepositories repositories;

    @BeforeEach
    void setUp() {
        lostClaims = mock(LostItemClaimRepository.class);
        foundClaims = mock(FoundItemClaimRepository.class);
        notifier = mock(ClaimNotifier.class);
        archiver = mock(ItemArchiver.class);
        repositories = new ClaimRepositories(lostClaims, foundClaims);
    }

    @Test
    void routingKeysFollowTheKindAndTheVerb() {
        assertThat(new ClaimedHandler(ItemKind.LOST, repositories, notifier).routingKey()).isEqualTo("item.lost.claimed");
        assertThat(new ReturnedHandler(ItemKind.FOUND, repositories, archiver).routingKey()).isEqualTo("item.found.returned");
    }

    @Test
    void aNewClaimIsPassedToTheAuthorOfItsOwnKind() {
        FoundItemClaim claim = Fixtures.foundClaim("+380671234567");
        when(foundClaims.findWithDetailsById(43L)).thenReturn(Optional.of(claim));

        new ClaimedHandler(ItemKind.FOUND, repositories, notifier).handle(event(43L));

        verify(notifier).notifyAuthor(ItemKind.FOUND, claim);
        verify(lostClaims, never()).findWithDetailsById(any());
    }

    @Test
    void aClaimThatIsGoneOrAlreadyArchivedSendsNothing() {
        LostItemClaim archived = Fixtures.lostClaim("+380671234567");
        archived.moveToArchive(new LostItemHistory());
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(archived));
        when(lostClaims.findWithDetailsById(404L)).thenReturn(Optional.empty());
        ClaimedHandler handler = new ClaimedHandler(ItemKind.LOST, repositories, notifier);

        handler.handle(event(42L));
        handler.handle(event(404L));

        verify(notifier, never()).notifyAuthor(any(), any());
    }

    @Test
    void aConfirmedReturnArchivesTheNoticeOfTheClaim() {
        when(lostClaims.findWithDetailsById(42L)).thenReturn(Optional.of(Fixtures.lostClaim("+380671234567")));

        new ReturnedHandler(ItemKind.LOST, repositories, archiver).handle(event(42L));

        verify(archiver).archive(ItemKind.LOST, 1L);
    }

    @Test
    void aReturnOnAnAlreadyArchivedNoticeDoesNothing() {
        FoundItemClaim archived = Fixtures.foundClaim("+380671234567");
        archived.moveToArchive(new FoundItemHistory());
        when(foundClaims.findWithDetailsById(43L)).thenReturn(Optional.of(archived));
        when(foundClaims.findWithDetailsById(404L)).thenReturn(Optional.empty());
        ReturnedHandler handler = new ReturnedHandler(ItemKind.FOUND, repositories, archiver);

        handler.handle(event(43L));
        handler.handle(event(404L));

        verify(archiver, never()).archive(any(), anyLong());
    }

    private static ClaimEvent event(Long id) {
        ClaimEvent event = new ClaimEvent();
        event.setId(id);
        return event;
    }
}
