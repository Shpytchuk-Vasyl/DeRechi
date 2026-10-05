package org.shpytchuk.tests;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Client-API publishes item.*.created, Worker finds candidates and writes similar_item. */
class ItemMatchingIT extends StackIT {

    @Test
    void aNewLostItemIsMatchedWithAFoundOneNearby() {
        double lat = randomLat();
        double lon = randomLon();
        Item found = createItem(Kind.FOUND, "Чорний шкіряний гаманець", "WALLET", lat, lon);
        Item lost = createItem(Kind.LOST, "Чорний шкіряний гаманець", "WALLET", lat, lon);

        awaitMatch(found, lost);
    }

    @Test
    void aNewFoundItemIsMatchedWithALostOneNearby() {
        double lat = randomLat();
        double lon = randomLon();
        Item lost = createItem(Kind.LOST, "Ключі від авто з брелоком", "KEYS", lat, lon);
        Item found = createItem(Kind.FOUND, "Ключі від авто з брелоком", "KEYS", lat, lon);

        awaitMatch(found, lost);
    }

    private static void awaitMatch(Item found, Item lost) {
        await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(count(
                "select count(*) from similar_item where found_item_id = ? and lost_item_id = ?",
                found.id(), lost.id())).isEqualTo(1));
    }
}
