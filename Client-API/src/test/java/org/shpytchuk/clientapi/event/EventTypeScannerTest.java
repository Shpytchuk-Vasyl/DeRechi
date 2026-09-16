package org.shpytchuk.clientapi.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeScannerTest {

    @Test
    void picksUpAnnotatedEventsSoRabbitCanResolveThemByTypeId() {
        Map<String, Class<?>> mapping = EventTypeScanner.scan();

        assertThat(mapping).containsEntry("ITEM_CREATED", ItemCreatedEvent.class);
    }
}
