package org.shpytchuk.worker.repository.found;

import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.repository.ThingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoundItemRepository extends ThingRepository<FoundItem> {
}
