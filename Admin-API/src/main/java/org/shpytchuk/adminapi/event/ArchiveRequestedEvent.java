package org.shpytchuk.adminapi.event;

@EventType("ARCHIVE_REQUESTED")
public record ArchiveRequestedEvent(Long id, String actor) {
}
