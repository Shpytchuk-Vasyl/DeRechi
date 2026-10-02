package org.shpytchuk.adminapi.repository.matching;

import org.shpytchuk.adminapi.entity.matching.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
public interface ClaimRepository<C extends Claim> extends JpaRepository<C, Long> {

    List<C> findByItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> itemIds);

    List<C> findByArchivedItemIdInOrderByCreatedAtDescIdDesc(Collection<Long> historyIds);

    List<C> findByItemId(Long itemId);

    List<C> findByArchivedItemId(Long historyId);
}
