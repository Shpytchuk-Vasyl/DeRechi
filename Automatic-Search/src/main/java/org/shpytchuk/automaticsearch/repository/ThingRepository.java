package org.shpytchuk.automaticsearch.repository;

import org.shpytchuk.automaticsearch.entity.Thing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface ThingRepository<T extends Thing> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
}
