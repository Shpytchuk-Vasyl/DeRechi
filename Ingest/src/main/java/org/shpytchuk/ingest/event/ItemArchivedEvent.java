package org.shpytchuk.ingest.event;

@EventType("ITEM_ARCHIVED")
public record ItemArchivedEvent(long id) {
}
