package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.SimilarItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SimilarItemRepository extends JpaRepository<SimilarItem, SimilarItem.SimilarItemId> {

    @Modifying
    @Query(value = """
            INSERT INTO similar_item (found_item_id, lost_item_id, match_order)
            SELECT *
            FROM unnest(CAST(:foundItemIds AS BIGINT[]),
                        CAST(:lostItemIds AS BIGINT[]),
                        CAST(:matchOrders AS DOUBLE PRECISION[]))
            ON CONFLICT (found_item_id, lost_item_id)
                DO UPDATE SET match_order = EXCLUDED.match_order
            """, nativeQuery = true)
    int insertAll(@Param("foundItemIds") Long[] foundItemIds,
                  @Param("lostItemIds") Long[] lostItemIds,
                  @Param("matchOrders") Double[] matchOrders);

}
