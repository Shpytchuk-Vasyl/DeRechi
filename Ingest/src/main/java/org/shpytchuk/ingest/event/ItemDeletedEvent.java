package org.shpytchuk.ingest.event;

@EventType("ITEM_DELETED")
public record ItemDeletedEvent(long id) {
}
