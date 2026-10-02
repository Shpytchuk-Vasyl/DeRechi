package org.shpytchuk.adminapi.repository.detail;

import org.shpytchuk.adminapi.entity.detail.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceRepository extends JpaRepository<Place, String> {
}
