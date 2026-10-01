package org.shpytchuk.dbpostgres.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.shpytchuk.dbpostgres.core.thing.FoundItem;
import org.shpytchuk.dbpostgres.history.FoundItemHistory;

@Entity
public class FoundItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItemHistory archivedItem;
}
