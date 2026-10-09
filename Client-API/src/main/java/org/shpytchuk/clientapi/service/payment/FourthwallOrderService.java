package org.shpytchuk.clientapi.service.payment;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.client.FourthwallClient;
import org.shpytchuk.clientapi.config.SiteProperties;
import org.shpytchuk.clientapi.controller.payment.FourthwallOrderPlaced;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.shpytchuk.clientapi.exeption.PaymentUnavailableException;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.payment.FourthwallOrderRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Service
@AllArgsConstructor
public class FourthwallOrderService {

    private static final Logger log = LoggerFactory.getLogger(FourthwallOrderService.class);

    static final Set<String> NOT_PAID = Set.of("CANCELLED");

    private final FourthwallOrderRepository orderRepository;
    private final LostItemClaimRepository lostClaimRepository;
    private final FoundItemClaimRepository foundClaimRepository;
    private final LostClaimService lostClaimService;
    private final FoundClaimService foundClaimService;
    private final TransactionTemplate transactionTemplate;
    private final FourthwallClient fourthwall;
    private final SiteProperties site;

    public void receive(FourthwallOrderPlaced order) {
        Optional<Unpaid> unpaid = transactionTemplate.execute(status -> record(order).map(this::unpaid));
        if (unpaid != null) {
            unpaid.ifPresent(pending -> serviceFor(pending.claim()).markPaid(pending.claim().getId())
                    .filter(paid -> !paid.repeated())
                    .ifPresent(paid -> {
                        log.info("Marked {} claim {} paid by Fourthwall order {}",
                                kind(pending.claim()), paid.id(), order.orderId());
                        closeSelfCancel(order.orderId(), pending.noticeUrl());
                    }));
        }
    }

    private void closeSelfCancel(String orderId, String noticeUrl) {
        try {
            fourthwall.markDownloaded(orderId, noticeUrl);
        } catch (PaymentUnavailableException e) {
            log.warn("Fourthwall order {} stays cancellable by the buyer: {}", orderId,
                    getMostSpecificCause(e).toString());
        }
    }

    private Unpaid unpaid(Claim<?> claim) {
        String url = claim.getItem() == null ? site.url() : site.notice(kind(claim), claim.getItem().getId());
        return new Unpaid(claim, url);
    }

    private static String kind(Claim<?> claim) {
        return claim instanceof LostItemClaim ? "lost" : "found";
    }

    private record Unpaid(Claim<?> claim, String noticeUrl) {
    }

    private Optional<Claim<?>> record(FourthwallOrderPlaced placed) {
        Optional<FourthwallOrder> existing = orderRepository.findByOrderId(placed.orderId());
        if (existing.isPresent()) {
            log.info("Fourthwall order {} was already received", placed.orderId());
            return existing.flatMap(FourthwallOrderService::claimOf).filter(claim -> claim.getPaidAt() == null);
        }

        FourthwallOrder order = orderRepository.save(toEntity(placed));
        Optional<Claim<?>> claim = match(placed);
        if (claim.isEmpty()) {
            log.warn("Unmatched Fourthwall order {}: {}", placed.orderId(), placed);
            return Optional.empty();
        }

        link(order, claim.get());
        log.info("Fourthwall order {} matched {} claim {}", placed.orderId(), kind(claim.get()), claim.get().getId());
        return claim.filter(matched -> matched.getPaidAt() == null);
    }

    private Optional<Claim<?>> match(FourthwallOrderPlaced placed) {
        if (NOT_PAID.contains(placed.status())) {
            log.warn("Fourthwall order {} is {}, it unlocks nothing", placed.orderId(), placed.status());
            return Optional.empty();
        }
        if (placed.itemIds().isEmpty()) {
            return Optional.empty();
        }
        Set<String> ids = placed.itemIds();
        return Stream.concat(
                        lostClaimRepository.findByPaymentProductIdInOrPaymentVariantIdIn(ids, ids).stream(),
                        foundClaimRepository.findByPaymentProductIdInOrPaymentVariantIdIn(ids, ids).stream())
                .<Claim<?>>map(claim -> claim)
                .findFirst();
    }

    private ClaimService<?, ?> serviceFor(Claim<?> claim) {
        return claim instanceof LostItemClaim ? lostClaimService : foundClaimService;
    }

    private static void link(FourthwallOrder order, Claim<?> claim) {
        switch (claim) {
            case LostItemClaim lost -> order.setLostItemClaim(lost);
            case FoundItemClaim found -> order.setFoundItemClaim(found);
            default -> throw new IllegalStateException("Unknown claim type " + claim.getClass());
        }
    }

    private static Optional<Claim<?>> claimOf(FourthwallOrder order) {
        return Optional.<Claim<?>>ofNullable(order.getLostItemClaim())
                .or(() -> Optional.ofNullable(order.getFoundItemClaim()));
    }

    private static FourthwallOrder toEntity(FourthwallOrderPlaced placed) {
        FourthwallOrder order = new FourthwallOrder();
        order.setOrderId(truncate(placed.orderId(), 64));
        order.setFriendlyId(truncate(placed.friendlyId(), 64));
        order.setStatus(truncate(placed.status(), 40));
        order.setEmail(truncate(placed.email(), 100));
        order.setUsername(truncate(placed.username(), 100));
        order.setAmount(placed.amount());
        order.setCurrency(truncate(placed.currency(), 3));
        order.setTestMode(placed.testMode());
        order.setPlacedAt(placed.placedAt());
        order.setReceivedAt(Instant.now());
        return order;
    }

    private static String truncate(String value, int length) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= length ? stripped : stripped.substring(0, length);
    }
}
