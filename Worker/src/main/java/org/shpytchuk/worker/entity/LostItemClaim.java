package org.shpytchuk.worker.entity;

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
