package org.shpytchuk.worker.entity.claim;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.worker.entity.core.ContactInfo;
import org.shpytchuk.worker.entity.core.thing.Thing;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ContactInfo contactInfo;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant authorRemindedAt;

    @Column
    private Instant claimantRemindedAt;

    @Column
    private Instant confirmedAt;

    public abstract Thing getItem();

    public abstract Thing getArchivedItem();

    public boolean isLive() {
        return getItem() != null;
    }

    public abstract void moveToArchive(Thing history);
}
