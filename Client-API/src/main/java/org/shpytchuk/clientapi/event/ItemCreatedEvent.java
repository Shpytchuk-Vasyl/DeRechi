package org.shpytchuk.clientapi.event;

import org.shpytchuk.clientapi.dto.ItemDto;

import java.time.LocalDate;

public class ItemCreatedEvent {
    public Long id;
    public LocalDate date;
    public Long category;
    public Double lat;
    public Double lon;

    public ItemCreatedEvent() {}

    public ItemCreatedEvent(ItemDto dto) {
        this.id = dto.id();
        this.date = dto.date();
        this.category = dto.category().id();
        this.lat = dto.place().lat();
        this.lon = dto.place().lon();
    }
}
