package org.shpytchuk.worker.entity.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.entity.core.thing.FoundItem;
import org.shpytchuk.worker.entity.core.thing.LostItem;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
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
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SimilarItemId implements Serializable {

        private Long foundItemId;
        private Long lostItemId;

        public SimilarItemId(Long foundItemId, Long lostItemId) {
            this.foundItemId = foundItemId;
            this.lostItemId = lostItemId;
        }

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
