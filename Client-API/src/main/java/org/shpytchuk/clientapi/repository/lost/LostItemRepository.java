package org.shpytchuk.clientapi.repository.lost;

import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
