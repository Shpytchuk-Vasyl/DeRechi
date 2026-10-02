package org.shpytchuk.worker.entity.found;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.Thing;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FoundItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItemHistory archivedItem;

    @Override
    public void moveToArchive(Thing history) {
        this.archivedItem = (FoundItemHistory) history;
        this.item = null;
    }
}
