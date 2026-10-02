package org.shpytchuk.dbpostgres.found;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import org.shpytchuk.dbpostgres.matching.Claim;

@Entity
public class FoundItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItemHistory archivedItem;
}
