package org.shpytchuk.adminapi.event;

@EventType("ITEM_DELETED")
public record ItemDeletedEvent(Long id) {
}
