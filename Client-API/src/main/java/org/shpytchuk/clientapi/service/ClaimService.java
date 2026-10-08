package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.client.FourthwallProduct;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.mapper.ClaimMapper;
import org.shpytchuk.clientapi.repository.ClaimRepository;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.shpytchuk.clientapi.service.payment.ClaimUnlockLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ClaimService<T extends Thing, C extends Claim<T>> {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private final ThingRepository<T> itemRepository;
    private final ClaimRepository<C> claimRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ClaimUnlockLimiter unlockLimiter;
    private final Supplier<C> factory;
    private final String entityName;

    @Transactional
    public ClaimDto claim(Long itemId, ContactInfoInput contact) {
        T item = itemRepository.findById(itemId).orElseThrow(() -> new NotFoundException(entityName, itemId));

        Optional<C> repeat = claimRepository.findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(itemId, contact.phone())
                .or(() -> claimRepository.findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(itemId, contact.email()));
        if (repeat.isPresent()) {
            log.info("Repeated claim {} on {} item {}", repeat.get().getId(), kind(repeat.get()), itemId);
            return ClaimMapper.toDto(repeat.get(), true);
        }

        ContactInfo info = new ContactInfo();
        info.setPhone(contact.phone());
        info.setEmail(contact.email());
        info.setSocialMedias(toArray(contact.socialMedias()));
        contactInfoRepository.save(info);

        C claim = factory.get();
        claim.setItem(item);
        claim.setContactInfo(info);
        claim.setToken(UUID.randomUUID().toString());
        claim.setCreatedAt(Instant.now());
        claimRepository.save(claim);
        log.info("Claim {} created on {} item {}", claim.getId(), kind(claim), itemId);
        return ClaimMapper.toDto(claim, false);
    }

    @Transactional
    public Optional<ClaimDto> confirm(String token) {
        return claimRepository.findByToken(token).map(claim -> {
            if (claim.getConfirmedAt() != null) {
                log.debug("Return on {} claim {} was already confirmed", kind(claim), claim.getId());
                return ClaimMapper.toDto(claim, true);
            }
            claim.setConfirmedAt(Instant.now());
            claimRepository.save(claim);
            log.info("Return confirmed on {} claim {}", kind(claim), claim.getId());
            return ClaimMapper.toDto(claim, false);
        });
    }

    @Transactional
    public Optional<ClaimDto> markPaid(Long claimId) {
        return claimRepository.findById(claimId).map(claim -> {
            if (claim.getPaidAt() != null) {
                return ClaimMapper.toDto(claim, true);
            }
            claim.setPaidAt(Instant.now());
            claimRepository.save(claim);
            return ClaimMapper.toDto(claim, false);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ClaimDto> find(Long itemId, Long claimId) {
        return claimRepository.findByIdAndItemId(claimId, itemId).map(claim -> ClaimMapper.toDto(claim, false));
    }

    public String claimEntityName() {
        return entityName + "Claim";
    }

    @Transactional
    public void reserveCheckout(Long claimId) {
        C claim = claimRepository.findWithContactInfoById(claimId)
                .orElseThrow(() -> new NotFoundException(claimEntityName(), claimId));
        if (claim.getPaymentRequestedAt() != null) {
            return;
        }
        unlockLimiter.check(claim.getContactInfo());
        claim.setPaymentRequestedAt(Instant.now());
        claimRepository.save(claim);
        log.info("Checkout reserved for {} claim {}", kind(claim), claimId);
    }

    @Transactional
    public void releaseCheckout(Long claimId) {
        claimRepository.findById(claimId)
                .filter(claim -> claim.getPaymentVariantId() == null)
                .ifPresent(claim -> {
                    claim.setPaymentRequestedAt(null);
                    claimRepository.save(claim);
                    log.info("Checkout of {} claim {} released after a failed Fourthwall call", kind(claim), claimId);
                });
    }

    @Transactional
    public ClaimDto attachProduct(Long claimId, FourthwallProduct product) {
        C claim = claimRepository.findById(claimId).orElseThrow(() -> new NotFoundException(claimEntityName(), claimId));
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

    private static String kind(Claim<?> claim) {
        return claim instanceof LostItemClaim ? "lost" : "found";
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }
}
