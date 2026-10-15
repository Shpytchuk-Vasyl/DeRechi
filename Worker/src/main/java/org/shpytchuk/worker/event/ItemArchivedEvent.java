package org.shpytchuk.worker.event;

@EventType("ITEM_ARCHIVED")
public record ItemArchivedEvent(Long id) {
}
