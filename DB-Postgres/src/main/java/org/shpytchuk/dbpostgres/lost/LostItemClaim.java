package org.shpytchuk.dbpostgres.lost;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.shpytchuk.dbpostgres.matching.Claim;

@Entity
public class LostItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItemHistory archivedItem;
}
