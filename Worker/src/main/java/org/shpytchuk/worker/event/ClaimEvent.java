package org.shpytchuk.worker.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EventType("CLAIM")
public class ClaimEvent {
    private Long id;
}
