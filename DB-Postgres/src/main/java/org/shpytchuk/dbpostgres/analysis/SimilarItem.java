package org.shpytchuk.dbpostgres.analysis;

import jakarta.persistence.*;
import org.shpytchuk.dbpostgres.core.thing.FoundItem;
import org.shpytchuk.dbpostgres.core.thing.LostItem;

import java.io.Serializable;
import java.util.Objects;

@Entity
public class SimilarItem {

    @EmbeddedId
    private SimilarItemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("foundItemId")
    private FoundItem foundItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lostItemId")
    private LostItem lostItem;

    @Column(nullable = false)
    private Double matchOrder;

    @Embeddable
    public static class SimilarItemId implements Serializable {

        private Long foundItemId;
        private Long lostItemId;

        @Override
        public boolean equals(Object o) {
            return o instanceof SimilarItemId other
                    && Objects.equals(foundItemId, other.foundItemId)
                    && Objects.equals(lostItemId, other.lostItemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(foundItemId, lostItemId);
        }
    }
}
