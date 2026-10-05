package org.shpytchuk.clientapi.repository;

import org.shpytchuk.clientapi.entity.Claim;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@NoRepositoryBean
public interface ClaimRepository<C extends Claim<?>> extends JpaRepository<C, Long> {

    Optional<C> findByToken(String token);

    Optional<C> findByIdAndItemId(Long id, Long itemId);

    @EntityGraph(attributePaths = "contactInfo")
    Optional<C> findWithContactInfoById(Long id);

    Optional<C> findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(Long itemId, String phone);

    Optional<C> findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(Long itemId, String email);

    List<C> findByPaymentProductIdInOrPaymentVariantIdIn(Collection<String> productIds, Collection<String> variantIds);

    @Query("""
            select c.paymentRequestedAt from #{#entityName} c
            where c.paymentRequestedAt > :since
              and (c.contactInfo.phone = :phone or lower(c.contactInfo.email) = lower(:email))
            """)
    List<Instant> findPaymentRequestedSince(String phone, String email, Instant since);
}
