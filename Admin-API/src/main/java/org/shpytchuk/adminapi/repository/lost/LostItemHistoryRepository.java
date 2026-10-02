package org.shpytchuk.adminapi.repository.lost;

import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemHistoryRepository extends ThingRepository<LostItemHistory> {
}
