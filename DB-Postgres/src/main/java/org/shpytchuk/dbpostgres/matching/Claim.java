package org.shpytchuk.dbpostgres.matching;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.shpytchuk.dbpostgres.detail.ContactInfo;

import java.time.Instant;

@MappedSuperclass
public abstract class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ContactInfo contactInfo;

    @NotBlank
    @Size(min = 36, max = 36)
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

    @Size(max = 64)
    @Column(length = 64)
    private String paymentProductId;

    @Size(max = 64)
    @Column(unique = true, length = 64)
    private String paymentVariantId;

    @Column
    private Instant paymentRequestedAt;

    @Column
    private Instant paidAt;

    @Column
    private Instant contactsSentAt;
}
