package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.clientapi.client.FourthwallProduct;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.ItemKind;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.mapper.ClaimMapper;
import org.shpytchuk.clientapi.mapper.ContactInfoMapper;
import org.shpytchuk.clientapi.repository.ClaimRepository;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockLimiter;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@AllArgsConstructor
@Slf4j
public abstract class ClaimService<T extends Thing, C extends Claim<T>> {

    private final ThingRepository<T> itemRepository;
    private final ClaimRepository<C> claimRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ClaimUnlockLimiter unlockLimiter;
    private final Supplier<C> factory;
    private final ItemKind kind;

    public ItemKind kind() {
        return kind;
    }

    @Transactional
    public ClaimDto claim(Long itemId, ContactInfoInput contact) {
        T item = itemRepository.findById(itemId).orElseThrow(() -> new NotFoundException(kind.entityName(), itemId));

        Optional<C> repeat = claimRepository.findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(itemId, contact.phone())
                .or(() -> claimRepository.findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(itemId, contact.email()));
        if (repeat.isPresent()) {
            log.info("Repeated claim {} on {} item {}", repeat.get().getId(), kind.label(), itemId);
            return ClaimMapper.toDto(repeat.get(), true);
        }

        ContactInfo info = contactInfoRepository.save(ContactInfoMapper.toEntity(contact));

        C claim = factory.get();
        claim.setItem(item);
        claim.setContactInfo(info);
        claim.setToken(UUID.randomUUID().toString());
        claim.setCreatedAt(Instant.now());
        claimRepository.save(claim);
        log.info("Claim {} created on {} item {}", claim.getId(), kind.label(), itemId);
        return ClaimMapper.toDto(claim, false);
    }

    @Transactional
    public Optional<ClaimDto> confirm(String token) {
        return claimRepository.findByToken(token).map(claim -> {
            ClaimDto confirmed = stampOnce(claim, Claim::getConfirmedAt, Claim::setConfirmedAt);
            if (confirmed.repeated()) {
                log.debug("Return on {} claim {} was already confirmed", kind.label(), claim.getId());
            } else {
                log.info("Return confirmed on {} claim {}", kind.label(), claim.getId());
            }
            return confirmed;
        });
    }

    @Transactional
    public Optional<ClaimDto> markPaid(Long claimId) {
        return claimRepository.findById(claimId).map(claim -> stampOnce(claim, Claim::getPaidAt, Claim::setPaidAt));
    }

    @Transactional(readOnly = true)
    public Optional<ClaimDto> find(Long itemId, Long claimId) {
        return claimRepository.findByIdAndItemId(claimId, itemId).map(claim -> ClaimMapper.toDto(claim, false));
    }

    @Transactional(readOnly = true)
    public Optional<C> findByPaymentIds(Set<String> ids) {
        return claimRepository.findByPaymentProductIdInOrPaymentVariantIdIn(ids, ids).stream().findFirst();
    }

    public abstract void link(FourthwallOrder order, C claim);

    public abstract Optional<C> claimOf(FourthwallOrder order);

    @Transactional
    public void reserveCheckout(Long claimId) {
        C claim = claimRepository.findWithContactInfoById(claimId)
                .orElseThrow(() -> new NotFoundException(kind.claimEntityName(), claimId));
        if (claim.getPaymentRequestedAt() != null) {
            return;
        }
        unlockLimiter.check(claim.getContactInfo());
        claim.setPaymentRequestedAt(Instant.now());
        claimRepository.save(claim);
        log.info("Checkout reserved for {} claim {}", kind.label(), claimId);
    }

    @Transactional
    public void releaseCheckout(Long claimId) {
        claimRepository.findById(claimId)
                .filter(claim -> claim.getPaymentVariantId() == null)
                .ifPresent(claim -> {
                    claim.setPaymentRequestedAt(null);
                    claimRepository.save(claim);
                    log.info("Checkout of {} claim {} released after a failed Fourthwall call", kind.label(), claimId);
                });
    }

    @Transactional
    public ClaimDto attachProduct(Long claimId, FourthwallProduct product) {
        C claim = claimRepository.findById(claimId).orElseThrow(() -> new NotFoundException(kind.claimEntityName(), claimId));
        if (claim.getPaymentVariantId() == null) {
            claim.setPaymentProductId(product.productId());
            claim.setPaymentVariantId(product.variantId());
            if (claim.getPaymentRequestedAt() == null) {
                claim.setPaymentRequestedAt(Instant.now());
            }
            claimRepository.save(claim);
        }
        return ClaimMapper.toDto(claim, false);
    }

    private ClaimDto stampOnce(C claim, Function<C, Instant> stamp, BiConsumer<C, Instant> setStamp) {
        if (stamp.apply(claim) != null) {
            return ClaimMapper.toDto(claim, true);
        }
        setStamp.accept(claim, Instant.now());
        claimRepository.save(claim);
        return ClaimMapper.toDto(claim, false);
    }
}
