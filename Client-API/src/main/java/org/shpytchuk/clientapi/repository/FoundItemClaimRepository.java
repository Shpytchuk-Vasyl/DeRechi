package org.shpytchuk.clientapi.repository;

import org.shpytchuk.clientapi.entity.FoundItemClaim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FoundItemClaimRepository extends ClaimRepository<FoundItemClaim> {

    @Override
    Optional<FoundItemClaim> findByToken(String token);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    Optional<FoundItemClaim> findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(Long itemId, String phone);

    @Override
    @EntityGraph(attributePaths = "contactInfo")
    Optional<FoundItemClaim> findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(Long itemId, String email);
}
