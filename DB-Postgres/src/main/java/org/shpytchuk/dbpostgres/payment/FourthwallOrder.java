package org.shpytchuk.dbpostgres.payment;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.shpytchuk.dbpostgres.found.FoundItemClaim;
import org.shpytchuk.dbpostgres.lost.LostItemClaim;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class FourthwallOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Size(max = 64)
    @Column(length = 64)
    private String friendlyId;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String status;

    @Size(max = 100)
    @Column(length = 100)
    private String email;

    @Size(max = 100)
    @Column(length = 100)
    private String username;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean testMode;

    @Column(nullable = false)
    private Instant placedAt;

    @Column(nullable = false)
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private LostItemClaim lostItemClaim;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FoundItemClaim foundItemClaim;
}
