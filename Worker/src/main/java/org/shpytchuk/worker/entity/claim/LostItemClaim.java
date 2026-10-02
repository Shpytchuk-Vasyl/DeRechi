package org.shpytchuk.worker.entity.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.entity.core.thing.LostItem;
import org.shpytchuk.worker.entity.core.thing.Thing;
import org.shpytchuk.worker.entity.history.LostItemHistory;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItemClaim extends Claim {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItemHistory archivedItem;

    @Override
    public void moveToArchive(Thing history) {
        this.archivedItem = (LostItemHistory) history;
        this.item = null;
    }
}
