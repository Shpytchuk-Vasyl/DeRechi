package org.shpytchuk.worker.repository.found;

import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.repository.ClaimRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoundItemClaimRepository extends ClaimRepository<FoundItemClaim> {

    @Override
    @EntityGraph(attributePaths = {"contactInfo", "item", "item.info", "archivedItem", "archivedItem.info"})
    Optional<FoundItemClaim> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<FoundItemClaim> findByItemId(Long itemId);

    @Override
    List<FoundItemClaim> findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    @Override
    List<FoundItemClaim> findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    @Override
    List<FoundItemClaim> findByItemNotNullAndConfirmedAtNotNull();

    @Override
    List<FoundItemClaim> findByItemNotNullAndCreatedAtLessThanEqual(Instant cutoff);

    @Override
    boolean existsByItemIdAndCreatedAtGreaterThan(Long itemId, Instant cutoff);

    @Override
    List<FoundItemClaim> findByCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);
}
