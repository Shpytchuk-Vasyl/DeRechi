package org.shpytchuk.worker.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.service.ItemData;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@EventType("ITEM_CREATED")
public class ItemCreatedEvent implements ItemData {
    private Long id;
    private LocalDate date;
    private Long category;
    private Double lat;
    private Double lon;
    private String title;
    private String description;
    private String language;
}
