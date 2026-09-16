package org.shpytchuk.adminapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
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

    @Column
    private Instant notifiedAt;

    @Column
    private String notifiedBy;

    public boolean isNotified() {
        return notifiedAt != null;
    }

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
