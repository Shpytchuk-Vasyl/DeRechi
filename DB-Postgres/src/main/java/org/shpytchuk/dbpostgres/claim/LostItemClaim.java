package org.shpytchuk.dbpostgres.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.shpytchuk.dbpostgres.core.thing.LostItem;
import org.shpytchuk.dbpostgres.history.LostItemHistory;

@Entity
public class LostItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItemHistory archivedItem;
}
