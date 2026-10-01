package org.shpytchuk.clientapi.entity;

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
public class LostItemClaim extends Claim<LostItem> {

    @ManyToOne(fetch = FetchType.LAZY)
    private LostItem item;
}
