package org.shpytchuk.adminapi.repository;

import org.shpytchuk.adminapi.entity.Thing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface ThingRepository<T extends Thing> extends JpaRepository<T, Long> {

    @Override
    @EntityGraph(attributePaths = {"info", "place", "category"})
    Page<T> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"info", "place", "category"})
    Optional<T> findWithDetailsById(Long id);
}
