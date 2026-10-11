package org.shpytchuk.clientapi.service;

import org.shpytchuk.clientapi.entity.ItemKind;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ClaimServices {

    private final Map<ItemKind, ClaimService<?, ?>> byKind;

    public ClaimServices(List<ClaimService<?, ?>> services) {
        Map<ItemKind, ClaimService<?, ?>> byKind = new EnumMap<>(ItemKind.class);
        for (ClaimService<?, ?> service : services) {
            ClaimService<?, ?> twin = byKind.putIfAbsent(service.kind(), service);
            if (twin != null) {
                throw new IllegalStateException("Two claim services for " + service.kind() + ": "
                        + twin.getClass().getName() + " and " + service.getClass().getName());
            }
        }
        for (ItemKind kind : ItemKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException("No claim service for " + kind);
            }
        }
        this.byKind = Collections.unmodifiableMap(byKind);
    }

    public ClaimService<?, ?> of(ItemKind kind) {
        return byKind.get(kind);
    }

    public Collection<ClaimService<?, ?>> all() {
        return byKind.values();
    }
}
