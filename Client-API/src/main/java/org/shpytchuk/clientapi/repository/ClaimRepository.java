package org.shpytchuk.clientapi.repository;

import org.shpytchuk.clientapi.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@NoRepositoryBean
public interface ClaimRepository<C extends Claim<?>> extends JpaRepository<C, Long> {

    Optional<C> findByToken(String token);

    Optional<C> findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(Long itemId, String phone);

    Optional<C> findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(Long itemId, String email);

    List<C> findByPaymentProductIdInOrPaymentVariantIdIn(Collection<String> productIds, Collection<String> variantIds);
}
