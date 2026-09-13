package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.LostItem;
import org.springframework.stereotype.Repository;

@Repository
public interface LostItemRepository extends ThingRepository<LostItem> {
}
