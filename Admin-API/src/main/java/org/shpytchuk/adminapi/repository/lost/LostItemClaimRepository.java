package org.shpytchuk.adminapi.repository.lost;

import org.shpytchuk.adminapi.entity.lost.LostItemClaim;
import org.shpytchuk.adminapi.repository.matching.ClaimRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LostItemClaimRepository extends ClaimRepository<LostItemClaim> {

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<LostItemClaim> findByItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> itemIds);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<LostItemClaim> findByArchivedItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> historyIds);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<LostItemClaim> findByItemId(Long itemId);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<LostItemClaim> findByArchivedItemId(Long historyId);
}
