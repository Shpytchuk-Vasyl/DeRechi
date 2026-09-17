package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
