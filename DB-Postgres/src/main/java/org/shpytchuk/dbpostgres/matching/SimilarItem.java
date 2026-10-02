package org.shpytchuk.dbpostgres.matching;

import jakarta.persistence.*;
import org.shpytchuk.dbpostgres.found.FoundItem;
import org.shpytchuk.dbpostgres.lost.LostItem;

import java.io.Serializable;
import java.time.Instant;
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

    @Column
    private Instant notifiedAt;

    @Column(length = 100)
    private String notifiedBy;

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
