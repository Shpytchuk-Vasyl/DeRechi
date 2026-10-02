package org.shpytchuk.worker.repository.found;

import org.shpytchuk.worker.entity.found.FoundItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemHistoryRepository extends JpaRepository<FoundItemHistory, Long> {
}
