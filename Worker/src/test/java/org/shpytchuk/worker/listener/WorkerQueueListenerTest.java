package org.shpytchuk.worker.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.event.ArchiveRequestedEvent;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.event.ItemUpdatedEvent;
import org.shpytchuk.worker.handler.ClaimHandler;
import org.shpytchuk.worker.handler.ItemCreatedHandler;
import org.shpytchuk.worker.handler.ItemUpdatedHandler;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerQueueListenerTest {

    private ItemCreatedHandler lostCreated;
    private ItemCreatedHandler foundCreated;
    private ItemUpdatedHandler lostUpdated;
    private ItemUpdatedHandler foundUpdated;
    private ClaimHandler claimed;
    private ClaimHandler returned;
    private ItemArchiver archiver;
    private WorkerQueueListener listener;

    @BeforeEach
    void setUp() {
        lostCreated = createdHandler(ItemKind.LOST);
        foundCreated = createdHandler(ItemKind.FOUND);
        lostUpdated = updatedHandler(ItemKind.LOST);
        foundUpdated = updatedHandler(ItemKind.FOUND);
        claimed = claimHandler("claim.lost.claimed");
        returned = claimHandler("notice.found.returned");
        archiver = mock(ItemArchiver.class);
        listener = new WorkerQueueListener(List.of(lostCreated, foundCreated), List.of(lostUpdated, foundUpdated),
                List.of(claimed, returned), archiver);
    }

    @Test
    void dispatchesACreatedNoticeByTheReceivedRoutingKey() {
        ItemCreatedEvent event = new ItemCreatedEvent();

        listener.onItemCreated(event, message("notice.found.created"));

        verify(foundCreated).onItemCreated(event);
        verify(lostCreated, never()).onItemCreated(any());
    }

    @Test
    void dispatchesAnEditedNoticeByTheReceivedRoutingKey() {
        ItemUpdatedEvent event = new ItemUpdatedEvent();

        listener.onItemUpdated(event, message("notice.lost.updated"));

        verify(lostUpdated).onItemUpdated(event);
        verify(foundUpdated, never()).onItemUpdated(any());
        verify(lostCreated, never()).onItemCreated(any());
    }

    @Test
    void dispatchesAClaimByTheReceivedRoutingKey() {
        ClaimEvent event = claimEvent(7L);

        listener.onClaim(event, message("notice.found.returned"));

        verify(returned).handle(event);
        verify(claimed, never()).handle(any());
    }

    @Test
    void archivesTheKindNamedByTheRoutingKey() {
        listener.onArchiveRequested(archiveEvent(7L), message("notice.found.archive"));

        verify(archiver).archive(ItemKind.FOUND, 7L);
    }

    @Test
    void rejectsAKeyNobodyHandlesForTheTypeWithoutRequeue() {
        assertThatThrownBy(() -> listener.onItemCreated(new ItemCreatedEvent(), message("claim.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("claim.lost.claimed");
        assertThatThrownBy(() -> listener.onItemUpdated(new ItemUpdatedEvent(), message("notice.lost.created")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("notice.lost.created");
        assertThatThrownBy(() -> listener.onClaim(claimEvent(7L), message("notice.lost.archived")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("notice.lost.archived");
        assertThatThrownBy(() -> listener.onArchiveRequested(archiveEvent(7L), message("claim.lost.claimed")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("claim.lost.claimed");

        verify(lostCreated, never()).onItemCreated(any());
        verify(lostUpdated, never()).onItemUpdated(any());
        verify(claimed, never()).handle(any());
        verify(returned, never()).handle(any());
        verify(archiver, never()).archive(any(), anyLong());
    }

    @Test
    void rejectsAPayloadWithoutAHandlerWithoutRequeue() {
        assertThatThrownBy(() -> listener.onUnknown("payload", message("notice.lost.created")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining(String.class.getName())
                .hasMessageContaining("notice.lost.created");
    }

    @Test
    void refusesToStartWithTwoHandlersForOneKey() {
        assertThatThrownBy(() -> new WorkerQueueListener(
                List.of(lostCreated, createdHandler(ItemKind.LOST)), List.of(), List.of(), archiver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice.lost.created");
    }

    private static ItemCreatedHandler createdHandler(ItemKind kind) {
        ItemCreatedHandler handler = mock(ItemCreatedHandler.class);
        when(handler.kind()).thenReturn(kind);
        return handler;
    }

    private static ItemUpdatedHandler updatedHandler(ItemKind kind) {
        ItemUpdatedHandler handler = mock(ItemUpdatedHandler.class);
        when(handler.kind()).thenReturn(kind);
        return handler;
    }

    private static ClaimHandler claimHandler(String routingKey) {
        ClaimHandler handler = mock(ClaimHandler.class);
        when(handler.routingKey()).thenReturn(routingKey);
        return handler;
    }

    private static ClaimEvent claimEvent(Long id) {
        ClaimEvent event = new ClaimEvent();
        event.setId(id);
        return event;
    }

    private static ArchiveRequestedEvent archiveEvent(Long id) {
        ArchiveRequestedEvent event = new ArchiveRequestedEvent();
        event.setId(id);
        event.setActor("admin@derechi.local");
        return event;
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }
}
