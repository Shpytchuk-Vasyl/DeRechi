package org.shpytchuk.adminapi.security;

import java.util.Set;

public enum Scope {
    MATCH(Action.VIEW, Action.NOTIFY),
    LOST_ITEM(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE, Action.ARCHIVE),
    FOUND_ITEM(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE, Action.ARCHIVE),
    LOST_ITEM_HISTORY(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE),
    FOUND_ITEM_HISTORY(Action.VIEW, Action.CREATE, Action.EDIT, Action.DELETE);

    private final Set<Action> actions;

    Scope(Action... actions) {
        this.actions = Set.of(actions);
    }

    public Set<Action> actions() {
        return actions;
    }

    public boolean supports(Action action) {
        return actions.contains(action);
    }
}
