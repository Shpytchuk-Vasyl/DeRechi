package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemHistoryRepository extends ThingRepository<LostItemHistory> {
}
