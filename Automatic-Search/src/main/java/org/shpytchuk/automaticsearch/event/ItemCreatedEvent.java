package org.shpytchuk.automaticsearch.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@EventType("item.created")
public class ItemCreatedEvent {
    private Long id;
    private LocalDate date;
    private Long category;
    private Double lat;
    private Double lon;
    private String title;
}
