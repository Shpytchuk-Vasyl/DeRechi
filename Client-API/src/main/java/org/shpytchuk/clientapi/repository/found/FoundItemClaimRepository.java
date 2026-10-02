package org.shpytchuk.clientapi.repository.found;

import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.repository.ClaimRepository;
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
