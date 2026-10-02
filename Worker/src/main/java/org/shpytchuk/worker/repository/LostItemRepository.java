package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.LostItem;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
