package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.matching.SimilarItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Arrays;

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

    default int insertForLost(Long lostItemId, Long[] foundItemIds, Double[] matchOrders) {
        return insertAll(foundItemIds, repeat(lostItemId, foundItemIds.length), matchOrders);
    }

    default int insertForFound(Long foundItemId, Long[] lostItemIds, Double[] matchOrders) {
        return insertAll(repeat(foundItemId, lostItemIds.length), lostItemIds, matchOrders);
    }

    private static Long[] repeat(Long id, int times) {
        Long[] ids = new Long[times];
        Arrays.fill(ids, id);
        return ids;
    }

    @Modifying
    @Query("delete from SimilarItem s where s.id.lostItemId = :lostItemId")
    int deleteByLostItemId(@Param("lostItemId") Long lostItemId);

    @Modifying
    @Query("delete from SimilarItem s where s.id.foundItemId = :foundItemId")
    int deleteByFoundItemId(@Param("foundItemId") Long foundItemId);
}
