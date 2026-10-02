package org.shpytchuk.clientapi.entity.lost;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.clientapi.entity.Claim;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class LostItemClaim extends Claim<LostItem> {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;
}
