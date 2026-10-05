package org.shpytchuk.clientapi.repository.found;

import org.shpytchuk.clientapi.entity.found.FoundItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface FoundItemHistoryRepository extends JpaRepository<FoundItemHistory, Long> {

    long countByArchivedAtGreaterThanEqual(Instant since);
}
