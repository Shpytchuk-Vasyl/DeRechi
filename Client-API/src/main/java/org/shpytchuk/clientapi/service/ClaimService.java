package org.shpytchuk.clientapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.Claim;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.repository.ClaimRepository;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@AllArgsConstructor
public abstract class ClaimService<T extends Thing, C extends Claim<T>> {

    private final ThingRepository<T> itemRepository;
    private final ClaimRepository<C> claimRepository;
    private final ContactInfoRepository contactInfoRepository;
    private final Supplier<C> factory;
    private final String entityName;

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
        return new ClaimDto(claim.getId(), false);
    }

    @Transactional
    public Optional<ClaimDto> confirm(String token) {
        return claimRepository.findByToken(token).map(claim -> {
            if (claim.getConfirmedAt() != null) {
                return new ClaimDto(claim.getId(), true);
            }
            claim.setConfirmedAt(Instant.now());
            claimRepository.save(claim);
            return new ClaimDto(claim.getId(), false);
        });
    }

    private static SocialMediaEnum[] toArray(List<SocialMediaEnum> socialMedias) {
        return socialMedias == null ? null : socialMedias.toArray(SocialMediaEnum[]::new);
    }
}
