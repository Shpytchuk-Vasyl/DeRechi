package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.security.Scope;

import java.util.Locale;


public enum ItemKind {

    LOST_ITEM(Scope.LOST_ITEM, Path.LOST_ITEMS),
    FOUND_ITEM(Scope.FOUND_ITEM, Path.FOUND_ITEMS),
    LOST_ITEM_HISTORY(Scope.LOST_ITEM_HISTORY, Path.LOST_ITEMS_HISTORY),
    FOUND_ITEM_HISTORY(Scope.FOUND_ITEM_HISTORY, Path.FOUND_ITEMS_HISTORY);

    public static final class Path {

        public static final String LOST_ITEMS = "/admin/lost-items";
        public static final String FOUND_ITEMS = "/admin/found-items";
        public static final String LOST_ITEMS_HISTORY = "/admin/lost-items-history";
        public static final String FOUND_ITEMS_HISTORY = "/admin/found-items-history";

        private Path() {
        }
    }

    private final Scope scope;
    private final String basePath;
    /** {@code lost_item}: the scope name as the bundles spell it. */
    private final String key;

    ItemKind(Scope scope, String basePath) {
        this.scope = scope;
        this.basePath = basePath;
        this.key = scope.name().toLowerCase(Locale.ROOT);
    }

    public Scope scope() {
        return scope;
    }

    public String basePath() {
        return basePath;
    }

    /** {@code page.lost_item}: the page title. */
    public String titleKey() {
        return "page." + key;
    }

    /** {@code entity.LOST_ITEM}: the noun {@code NotFoundException} is rendered with. */
    public String entityKey() {
        return "entity." + scope.name();
    }

    /** {@code flash.lost_item.created}: the message shown after an action. */
    public String flashKey(String action) {
        return "flash." + key + "." + action;
    }

    /** {@code lost item}: how the kind is named in log lines. */
    public String label() {
        return key.replace('_', ' ');
    }
}
