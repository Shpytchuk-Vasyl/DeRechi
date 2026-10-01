package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.FoundItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemHistoryRepository extends JpaRepository<FoundItemHistory, Long> {
}
