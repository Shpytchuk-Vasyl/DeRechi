package org.shpytchuk.automaticsearch.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EventType("ARCHIVE_REQUESTED")
public class ArchiveRequestedEvent {
    private Long id;
    private String actor;
}
