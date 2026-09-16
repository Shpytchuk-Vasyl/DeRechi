package org.shpytchuk.adminapi.repository;

import org.shpytchuk.adminapi.entity.ThingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThingCategoryRepository extends JpaRepository<ThingCategory, Long> {

    List<ThingCategory> findAllByOrderByKeyAsc();
}
