package org.shpytchuk.clientapi.repository.detail;

import org.shpytchuk.clientapi.entity.detail.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceRepository extends JpaRepository<Place, String>, JpaSpecificationExecutor<Place> {
}
