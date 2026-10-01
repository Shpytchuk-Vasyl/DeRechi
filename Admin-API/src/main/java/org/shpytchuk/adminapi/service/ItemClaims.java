package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.ContactInfo;
import org.shpytchuk.adminapi.entity.items.Claim;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.ClaimRepository;
import org.shpytchuk.adminapi.view.ClaimView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ItemClaims<C extends Claim> {

    private final ClaimRepository<C> repository;
    private final ContactInfoRepository contactInfoRepository;
    private final Function<Collection<Long>, List<C>> byIds;
    private final Function<Long, List<C>> byId;
    private final Function<C, Long> itemIdOf;

    private ItemClaims(ClaimRepository<C> repository,
                       ContactInfoRepository contactInfoRepository,
                       Function<Collection<Long>, List<C>> byIds,
                       Function<Long, List<C>> byId,
                       Function<C, Long> itemIdOf) {
        this.repository = repository;
        this.contactInfoRepository = contactInfoRepository;
        this.byIds = byIds;
        this.byId = byId;
        this.itemIdOf = itemIdOf;
    }

    static <C extends Claim> ItemClaims<C> ofLive(ClaimRepository<C> repository,
                                                      ContactInfoRepository contactInfoRepository) {
        return new ItemClaims<>(repository, contactInfoRepository,
                repository::findByItemIdInOrderByCreatedAtDescIdDesc, repository::findByItemId,
                claim -> claim.getItem().getId());
    }

    static <C extends Claim> ItemClaims<C> ofArchived(ClaimRepository<C> repository,
                                                          ContactInfoRepository contactInfoRepository) {
        return new ItemClaims<>(repository, contactInfoRepository,
                repository::findByArchivedItemIdInOrderByCreatedAtDescIdDesc, repository::findByArchivedItemId,
                claim -> claim.getArchivedItem().getId());
    }

    Map<Long, List<ClaimView>> byItem(Collection<Long> itemIds) {
        Map<Long, List<ClaimView>> result = new LinkedHashMap<>();
        itemIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (itemIds.isEmpty()) {
            return result;
        }
        for (C claim : byIds.apply(itemIds)) {
            result.computeIfAbsent(itemIdOf.apply(claim), id -> new ArrayList<>())
                    .add(ItemMapper.toClaim(claim));
        }
        return result;
    }

    void deleteOf(Long itemId) {
        List<C> claims = byId.apply(itemId);
        if (claims.isEmpty()) {
            return;
        }
        List<ContactInfo> contacts = claims.stream().map(Claim::getContactInfo).toList();
        repository.deleteAll(claims);
        contactInfoRepository.deleteAll(contacts);
    }
}
