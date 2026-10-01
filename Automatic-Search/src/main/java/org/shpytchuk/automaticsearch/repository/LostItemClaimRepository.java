package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.LostItemClaim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LostItemClaimRepository extends ClaimRepository<LostItemClaim> {

    @Override
    @EntityGraph(attributePaths = {"contactInfo", "item", "item.info"})
    Optional<LostItemClaim> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    List<LostItemClaim> findByItemId(Long itemId);

    @Override
    List<LostItemClaim> findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    @Override
    List<LostItemClaim> findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    @Override
    List<LostItemClaim> findByItemNotNullAndConfirmedAtNotNull();

    @Override
    List<LostItemClaim> findByItemNotNullAndCreatedAtLessThanEqual(Instant cutoff);

    @Override
    boolean existsByItemIdAndCreatedAtGreaterThan(Long itemId, Instant cutoff);

    @Override
    List<LostItemClaim> findByCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);
}
