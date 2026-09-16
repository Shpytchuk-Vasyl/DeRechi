package org.shpytchuk.adminapi.repository;

import org.shpytchuk.adminapi.entity.LostItem;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
