package org.shpytchuk.clientapi.aspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.dto.CategoryDto;
import org.shpytchuk.clientapi.dto.ContactInfoDto;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.dto.PlaceDto;
import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.input.ItemInput;
import org.shpytchuk.clientapi.input.PlaceInput;
import org.shpytchuk.clientapi.service.FoundItemService;
import org.shpytchuk.clientapi.service.LostItemService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemEventAspectTest {

    private static final String EXCHANGE = "derechi.items";

    private static final ItemInput INPUT = new ItemInput(
            "Ключі", null, LocalDate.of(2026, 9, 1), 500, "keys.png", 2L,
            new PlaceInput("ChIJplaceId", "Площа Ринок", 49.8419, 24.0315),
            new ContactInfoInput("+380671234567", "finder@example.com", List.of()));

    private static final ItemDto CREATED = new ItemDto(
            1L, "Ключі", null, LocalDate.of(2026, 9, 1), 500, "keys.png",
            new CategoryDto(2L, "keys"),
            new PlaceDto("ChIJplaceId", "Площа Ринок", 49.8419, 24.0315),
            new ContactInfoDto(4L, "+380671234567", "finder@example.com"));

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private LostItemService lostItemService;

    @Mock
    private FoundItemService foundItemService;

    @Test
    void publishesLostItemsUnderTheirOwnRoutingKey() {
        given(lostItemService.create(INPUT)).willReturn(CREATED);

        proxy(lostItemService).create(INPUT);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.lost.created"), any(ItemCreatedEvent.class));
    }

    @Test
    void publishesFoundItemsUnderTheirOwnRoutingKey() {
        given(foundItemService.create(INPUT)).willReturn(CREATED);

        proxy(foundItemService).create(INPUT);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.found.created"), any(ItemCreatedEvent.class));
    }

    @Test
    void publishesTheFlattenedPayload() {
        given(lostItemService.create(INPUT)).willReturn(CREATED);
        ArgumentCaptor<ItemCreatedEvent> captor = ArgumentCaptor.forClass(ItemCreatedEvent.class);

        proxy(lostItemService).create(INPUT);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.lost.created"), captor.capture());
        ItemCreatedEvent event = captor.getValue();
        assertThat(event.id).isEqualTo(1L);
        assertThat(event.title).isEqualTo("Ключі");
        assertThat(event.category).isEqualTo(2L);
        assertThat(event.lat).isEqualTo(49.8419);
        assertThat(event.lon).isEqualTo(24.0315);
    }

    @Test
    void doesNotPublishWhenCreateThrows() {
        given(lostItemService.create(INPUT)).willThrow(new IllegalArgumentException("boom"));

        LostItemService proxy = proxy(lostItemService);
        try {
            proxy.create(INPUT);
        } catch (IllegalArgumentException expected) {
        }

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ItemEventAspect(rabbitTemplate));
        return factory.getProxy();
    }
}
