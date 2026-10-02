package org.shpytchuk.adminapi.repository.lost;

import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
