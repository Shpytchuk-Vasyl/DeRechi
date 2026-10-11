package org.shpytchuk.clientapi.entity.found;

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
public class FoundItemClaim extends Claim<FoundItem> {

    @ManyToOne(fetch = FetchType.LAZY)
    private FoundItem item;

    @Override
    public ItemKind kind() {
        return ItemKind.FOUND;
    }
}
