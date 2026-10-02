package org.shpytchuk.clientapi.repository.thing;

import org.shpytchuk.clientapi.entity.thing.Thing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface ThingRepository<T extends Thing> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    @Override
    @EntityGraph(attributePaths = {"info", "place", "category"})
    Page<T> findAll(Specification<T> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"info", "place", "category"})
    Optional<T> findWithDetailsById(Long id);
}
