package org.shpytchuk.adminapi.repository.found;

import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemRepository extends ThingRepository<FoundItem> {
}
