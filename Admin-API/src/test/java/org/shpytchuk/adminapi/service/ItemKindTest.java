package org.shpytchuk.adminapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.shpytchuk.adminapi.security.Scope;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keys are derived from the scope name, so a typo would only show up as a key name on the screen.
 * This pins them against the base bundle ({@code MessagesTest} keeps the other languages in step).
 */
class ItemKindTest {

    private static final Properties MESSAGES = load("messages.properties");

    @ParameterizedTest
    @EnumSource(ItemKind.class)
    void everyDerivedKeyExistsInTheBundle(ItemKind kind) {
        assertThat(MESSAGES).containsKeys(kind.titleKey(), kind.entityKey());
        for (String action : List.of("created", "updated", "archived", "deleted")) {
            assertThat(MESSAGES).containsKey(kind.flashKey(action));
        }
    }

    @Test
    void spellsTheKeysAndThePathsTheWayTheControllersAndBundlesDo() {
        assertThat(ItemKind.LOST_ITEM.scope()).isEqualTo(Scope.LOST_ITEM);
        assertThat(ItemKind.LOST_ITEM.basePath()).isEqualTo("/admin/lost-items");
        assertThat(ItemKind.LOST_ITEM.titleKey()).isEqualTo("page.lost_item");
        assertThat(ItemKind.LOST_ITEM.entityKey()).isEqualTo("entity.LOST_ITEM");
        assertThat(ItemKind.LOST_ITEM.flashKey("created")).isEqualTo("flash.lost_item.created");
        assertThat(ItemKind.LOST_ITEM.label()).isEqualTo("lost item");

        assertThat(ItemKind.FOUND_ITEM_HISTORY.basePath()).isEqualTo("/admin/found-items-history");
        assertThat(ItemKind.FOUND_ITEM_HISTORY.titleKey()).isEqualTo("page.found_item_history");
        assertThat(ItemKind.FOUND_ITEM_HISTORY.entityKey()).isEqualTo("entity.FOUND_ITEM_HISTORY");
        assertThat(ItemKind.FOUND_ITEM_HISTORY.label()).isEqualTo("found item history");
    }

    @Test
    void everyScopeButMatchIsAKind() {
        assertThat(ItemKind.values()).extracting(ItemKind::scope)
                .containsExactlyInAnyOrder(Scope.LOST_ITEM, Scope.FOUND_ITEM,
                        Scope.LOST_ITEM_HISTORY, Scope.FOUND_ITEM_HISTORY);
    }

    private static Properties load(String bundle) {
        Properties properties = new Properties();
        try (InputStream stream = ItemKindTest.class.getClassLoader().getResourceAsStream(bundle)) {
            if (stream == null) {
                throw new IllegalStateException(bundle + " not found on the classpath");
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalStateException("Could not read " + bundle, unreadable);
        }
        return properties;
    }
}
