package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.core.thing.FoundItem;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemRepository extends ThingRepository<FoundItem> {
}
