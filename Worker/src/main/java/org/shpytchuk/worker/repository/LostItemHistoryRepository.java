package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.history.LostItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemHistoryRepository extends JpaRepository<LostItemHistory, Long> {
}
