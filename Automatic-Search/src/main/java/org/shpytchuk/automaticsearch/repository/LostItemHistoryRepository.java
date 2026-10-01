package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.LostItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemHistoryRepository extends JpaRepository<LostItemHistory, Long> {
}
