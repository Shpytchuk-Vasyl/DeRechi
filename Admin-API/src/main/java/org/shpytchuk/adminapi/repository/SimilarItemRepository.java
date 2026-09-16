package org.shpytchuk.adminapi.repository;

import org.shpytchuk.adminapi.entity.SimilarItem;
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
    List<SimilarItem> findByLostItemIdInOrderByMatchOrderDesc(Collection<Long> lostItemIds);

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
