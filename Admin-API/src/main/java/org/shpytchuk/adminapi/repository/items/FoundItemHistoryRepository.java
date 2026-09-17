package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.FoundItemHistory;
import org.shpytchuk.adminapi.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemHistoryRepository extends ThingRepository<FoundItemHistory> {
}
