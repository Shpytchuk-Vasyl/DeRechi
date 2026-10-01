package org.shpytchuk.adminapi.entity.items;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
