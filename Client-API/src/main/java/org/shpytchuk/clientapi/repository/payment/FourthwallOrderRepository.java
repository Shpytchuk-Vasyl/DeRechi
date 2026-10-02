package org.shpytchuk.clientapi.repository.payment;

import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FourthwallOrderRepository extends JpaRepository<FourthwallOrder, Long> {

    Optional<FourthwallOrder> findByOrderId(String orderId);

    List<FourthwallOrder> findByLostItemClaimIdOrderByIdAsc(Long claimId);

    List<FourthwallOrder> findByFoundItemClaimIdOrderByIdAsc(Long claimId);
}
