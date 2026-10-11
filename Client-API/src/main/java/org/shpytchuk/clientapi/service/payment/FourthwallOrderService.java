package org.shpytchuk.clientapi.service.payment;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.clientapi.client.FourthwallClient;
import org.shpytchuk.clientapi.config.SiteProperties;
import org.shpytchuk.clientapi.controller.payment.FourthwallOrderPlaced;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.shpytchuk.clientapi.exeption.PaymentUnavailableException;
import org.shpytchuk.clientapi.repository.payment.FourthwallOrderRepository;
import org.shpytchuk.clientapi.service.ClaimService;
import org.shpytchuk.clientapi.service.ClaimServices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause;

@Service
@AllArgsConstructor
@Slf4j
public class FourthwallOrderService {

    static final Set<String> NOT_PAID = Set.of("CANCELLED");

    private final FourthwallOrderRepository orderRepository;
    private final ClaimServices claimServices;
    private final TransactionTemplate transactionTemplate;
    private final FourthwallClient fourthwall;
    private final SiteProperties site;

    public void receive(FourthwallOrderPlaced order) {
        Optional<Unpaid> unpaid = transactionTemplate.execute(status -> record(order).map(this::unpaid));
        if (unpaid != null) {
            unpaid.ifPresent(pending -> claimServices.of(pending.claim().kind()).markPaid(pending.claim().getId())
                    .filter(paid -> !paid.repeated())
                    .ifPresent(paid -> {
                        log.info("Marked {} claim {} paid by Fourthwall order {}",
                                pending.claim().kind().label(), paid.id(), order.orderId());
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
        String url = claim.getItem() == null ? site.url() : site.notice(claim.kind(), claim.getItem().getId());
        return new Unpaid(claim, url);
    }

    private record Unpaid(Claim<?> claim, String noticeUrl) {
    }

    private Optional<Claim<?>> record(FourthwallOrderPlaced placed) {
        Optional<FourthwallOrder> existing = orderRepository.findByOrderId(placed.orderId());
        if (existing.isPresent()) {
            log.info("Fourthwall order {} was already received", placed.orderId());
            return existing.flatMap(this::claimOf).filter(claim -> claim.getPaidAt() == null);
        }

        FourthwallOrder order = orderRepository.save(toEntity(placed));
        Optional<Claim<?>> claim = match(placed, order);
        if (claim.isEmpty()) {
            log.warn("Unmatched Fourthwall order {}: {}", placed.orderId(), placed);
            return Optional.empty();
        }

        log.info("Fourthwall order {} matched {} claim {}", placed.orderId(), claim.get().kind().label(), claim.get().getId());
        return claim.filter(matched -> matched.getPaidAt() == null);
    }

    private Optional<Claim<?>> match(FourthwallOrderPlaced placed, FourthwallOrder order) {
        if (NOT_PAID.contains(placed.status())) {
            log.warn("Fourthwall order {} is {}, it unlocks nothing", placed.orderId(), placed.status());
            return Optional.empty();
        }
        if (placed.itemIds().isEmpty()) {
            return Optional.empty();
        }
        return claimServices.all().stream()
                .map(service -> linkBought(service, order, placed.itemIds()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static <C extends Claim<?>> Optional<Claim<?>> linkBought(ClaimService<?, C> service, FourthwallOrder order, Set<String> ids) {
        Optional<C> claim = service.findByPaymentIds(ids);
        claim.ifPresent(bought -> service.link(order, bought));
        return claim.map(bought -> bought);
    }

    private Optional<Claim<?>> claimOf(FourthwallOrder order) {
        return claimServices.all().stream()
                .map(service -> service.claimOf(order))
                .flatMap(Optional::stream)
                .<Claim<?>>map(claim -> claim)
                .findFirst();
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
