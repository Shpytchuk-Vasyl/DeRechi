package org.shpytchuk.clientapi.repository;

import org.shpytchuk.clientapi.entity.LostItem;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
