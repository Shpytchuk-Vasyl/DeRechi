package org.shpytchuk.worker.repository;

import org.shpytchuk.worker.entity.thing.Thing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

@NoRepositoryBean
public interface ThingRepository<T extends Thing> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    @Query("""
            SELECT match_rank_cfg(:regconfig, t.title || ' ' || COALESCE(t.description, ''), :title)
            FROM #{#entityName} t
            WHERE t.id IN :ids
            ORDER BY match_rank_cfg(:regconfig, t.title || ' ' || COALESCE(t.description, ''), :title) DESC, t.id ASC
            """)
    List<Double> rankAll(@Param("ids") List<Long> ids,
                         @Param("regconfig") String regconfig,
                         @Param("title") String title);

    @Query("SELECT t.id FROM #{#entityName} t WHERE t.date < :cutoff ORDER BY t.id")
    List<Long> findIdsDatedBefore(@Param("cutoff") LocalDate cutoff);
}
