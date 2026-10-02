package org.shpytchuk.worker.repository.lost;

import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
