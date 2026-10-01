package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.FoundItemClaim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FoundItemClaimRepository extends ClaimRepository<FoundItemClaim> {

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<FoundItemClaim> findByItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> itemIds);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<FoundItemClaim> findByArchivedItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> historyIds);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<FoundItemClaim> findByItemId(Long itemId);
}
