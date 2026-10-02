package org.shpytchuk.adminapi.repository.found;

import org.shpytchuk.adminapi.entity.found.FoundItemClaim;
import org.shpytchuk.adminapi.repository.matching.ClaimRepository;
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

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<FoundItemClaim> findByArchivedItemId(Long historyId);
}
