package org.shpytchuk.clientapi.repository.found;

import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.repository.thing.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemRepository extends ThingRepository<FoundItem> {
}
