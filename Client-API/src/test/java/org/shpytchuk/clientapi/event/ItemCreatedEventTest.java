package org.shpytchuk.clientapi.event;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.ContactInfoDto;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.PlaceDto;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCreatedEventTest {

    @Test
    void flattensDtoIntoTheEventPayload() {
        ItemDto dto = new ItemDto(1L, "Ключі", "опис", LocalDate.of(2026, 9, 1), 500, "keys.png",
                new CategoryDto(2L, "keys"),
                new PlaceDto("ChIJplaceId", "Площа Ринок", 49.8419, 24.0315),
                new ContactInfoDto(4L, "+380671234567", "finder@example.com"));

        ItemCreatedEvent event = new ItemCreatedEvent(dto);

        assertThat(event.id).isEqualTo(1L);
        assertThat(event.title).isEqualTo("Ключі");
        assertThat(event.date).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(event.category).isEqualTo(2L);
        assertThat(event.lat).isEqualTo(49.8419);
        assertThat(event.lon).isEqualTo(24.0315);
    }
}
