package org.shpytchuk.clientapi.event;

import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.MoneyDto;

import java.time.LocalDate;

@EventType("ITEM_CREATED")
public class ItemCreatedEvent {
    public Long id;
    public LocalDate date;
    public Long category;
    public String categoryKey;
    public Double lat;
    public Double lon;
    public String placeName;
    public String title;
    public String description;
    public Integer compensation;
    public String currency;
    public String image;

    public ItemCreatedEvent(ItemDto dto) {
        this.id = dto.id();
        this.date = dto.date();
        this.category = dto.category().id();
        this.categoryKey = dto.category().key();
        this.lat = dto.place().lat();
        this.lon = dto.place().lon();
        this.placeName = dto.place().name();
        this.title = dto.title();
        this.description = dto.description();
        MoneyDto money = dto.compensation();
        this.compensation = money == null ? null : money.amount();
        this.currency = money == null ? null : money.currency();
        this.image = dto.image();
    }

    @Override
    public String toString() {
        return "ItemCreatedEvent[id=" + id + ", category=" + category + "]";
    }
}
