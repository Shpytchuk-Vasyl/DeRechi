package org.shpytchuk.adminapi.entity.found;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.adminapi.entity.matching.Claim;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItemHistory archivedItem;
}
