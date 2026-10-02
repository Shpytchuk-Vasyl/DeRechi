package org.shpytchuk.clientapi.repository.lost;

import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.repository.ClaimRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LostItemClaimRepository extends ClaimRepository<LostItemClaim> {

    @Override
    Optional<LostItemClaim> findByToken(String token);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    Optional<LostItemClaim> findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(Long itemId, String phone);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    Optional<LostItemClaim> findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(Long itemId, String email);
}
