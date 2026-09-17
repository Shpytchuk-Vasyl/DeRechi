package org.shpytchuk.adminapi.repository.items;

import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.view.CandidateCount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SimilarItemRepository extends JpaRepository<SimilarItem, SimilarItem.SimilarItemId> {

    @EntityGraph(attributePaths = {"foundItem", "foundItem.info", "foundItem.place", "foundItem.category"})
    @Query("""
            select s from SimilarItem s
            where s.id.lostItemId in :lostItemIds
              and (select count(o) from SimilarItem o
                   where o.id.lostItemId = s.id.lostItemId
                     and (o.matchOrder > s.matchOrder
                          or (o.matchOrder = s.matchOrder and o.id.foundItemId < s.id.foundItemId))) < :limit
            order by s.id.lostItemId, s.matchOrder desc, s.id.foundItemId
            """)
    List<SimilarItem> findTopByLostItemIdIn(@Param("lostItemIds") Collection<Long> lostItemIds,
                                            @Param("limit") long limit);

    @Query("""
            select new org.shpytchuk.adminapi.view.CandidateCount(s.id.lostItemId, count(s))
            from SimilarItem s
            where s.id.lostItemId in :lostItemIds
            group by s.id.lostItemId
            """)
    List<CandidateCount> countByLostItemIdIn(@Param("lostItemIds") Collection<Long> lostItemIds);

    @EntityGraph(attributePaths = {"foundItem", "foundItem.info", "foundItem.place", "foundItem.category"})
    List<SimilarItem> findByLostItemIdOrderByMatchOrderDescFoundItemIdAsc(@Param("lostItemId") Long lostItemId);

    @Override
    @EntityGraph(attributePaths = {
            "foundItem", "foundItem.info", "foundItem.place", "foundItem.category",
            "lostItem", "lostItem.info", "lostItem.place", "lostItem.category"})
    Optional<SimilarItem> findById(SimilarItem.SimilarItemId id);

    @Modifying
    @Query("delete from SimilarItem s where s.id.lostItemId = :lostItemId")
    int deleteByLostItemId(@Param("lostItemId") Long lostItemId);

    @Modifying
    @Query("delete from SimilarItem s where s.id.foundItemId = :foundItemId")
    int deleteByFoundItemId(@Param("foundItemId") Long foundItemId);
}
