package org.shpytchuk.clientapi.entity.lost;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.ItemKind;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItemClaim extends Claim<LostItem> {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;

    @Override
    public ItemKind kind() {
        return ItemKind.LOST;
    }
}
