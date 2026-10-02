package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.matching.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface ClaimRepository<C extends Claim> extends JpaRepository<C, Long> {

    Optional<C> findWithDetailsById(Long id);

    List<C> findByItemId(Long itemId);

    List<C> findByItemNotNullAndConfirmedAtNullAndAuthorRemindedAtNullAndCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    List<C> findByItemNotNullAndConfirmedAtNullAndClaimantRemindedAtNullAndAuthorRemindedAtLessThanEqualOrderByIdAsc(Instant cutoff);

    List<C> findByItemNotNullAndConfirmedAtNotNull();

    List<C> findByItemNotNullAndCreatedAtLessThanEqual(Instant cutoff);

    boolean existsByItemIdAndCreatedAtGreaterThan(Long itemId, Instant cutoff);

    List<C> findByCreatedAtLessThanEqualOrderByIdAsc(Instant cutoff);
}
