package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.core.thing.Thing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;

@NoRepositoryBean
public interface ThingRepository<T extends Thing> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    @Query("""
            SELECT ts_rank_cfg(:regconfig, t.title || ' ' || COALESCE(t.description, ''), :title)
            FROM #{#entityName} t
            WHERE t.id IN :ids
            ORDER BY ts_rank_cfg(:regconfig, t.title || ' ' || COALESCE(t.description, ''), :title) DESC, t.id ASC
            """)
    List<Double> rankAll(@Param("ids") List<Long> ids,
                         @Param("regconfig") String regconfig,
                         @Param("title") String title);
}
