package org.shpytchuk.worker.repository.lost;

import org.shpytchuk.worker.entity.lost.LostItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemHistoryRepository extends JpaRepository<LostItemHistory, Long> {
}
