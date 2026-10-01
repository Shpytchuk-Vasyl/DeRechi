package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.ContactInfo;
import org.shpytchuk.clientapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.Thing;
import org.shpytchuk.clientapi.event.ClaimChange;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.repository.ClaimRepository;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.ThingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ClaimService<T extends Thing, C extends Claim<T>> {

    private static final String CLAIMED = ".claimed";
    private static final String RETURNED = ".returned";

    private final ThingRepository<T> itemRepository;
    private final ClaimRepository<C> claimRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final ApplicationEventPublisher events;
    private final Supplier<C> factory;
    private final String entityName;
    private final String routingKeyPrefix;

    @Transactional
    public ClaimDto claim(Long itemId, ContactInfoInput contact) {
        T item = itemRepository.findById(itemId).orElseThrow(() -> new NotFoundException(entityName, itemId));

        Optional<C> repeat = claimRepository.findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(itemId, contact.phone())
                .or(() -> claimRepository.findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(itemId, contact.email()));
        if (repeat.isPresent()) {
            return new ClaimDto(repeat.get().getId(), true);
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

        events.publishEvent(new ClaimChange(routingKeyPrefix + CLAIMED, claim.getId()));
        return new ClaimDto(claim.getId(), false);
    }

    @Transactional
    public boolean confirm(String token) {
        Optional<C> found = claimRepository.findByToken(token);
        if (found.isEmpty()) {
            return false;
        }

        C claim = found.get();
        if (claim.getConfirmedAt() == null) {
            claim.setConfirmedAt(Instant.now());
            claimRepository.save(claim);
            events.publishEvent(new ClaimChange(routingKeyPrefix + RETURNED, claim.getId()));
        }
        return true;
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }
}
