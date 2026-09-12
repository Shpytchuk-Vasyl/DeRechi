package org.shpytchuk.clientapi.repository;

import org.shpytchuk.clientapi.entity.ThingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ThingCategoryRepository extends JpaRepository<ThingCategory, Long> {
}
