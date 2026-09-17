package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.FoundItem;
import org.shpytchuk.adminapi.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemRepository extends ThingRepository<FoundItem> {
}
