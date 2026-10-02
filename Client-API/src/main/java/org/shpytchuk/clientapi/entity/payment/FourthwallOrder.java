package org.shpytchuk.clientapi.entity.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class FourthwallOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(length = 64)
    private String friendlyId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

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
