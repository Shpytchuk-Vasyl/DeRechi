package org.shpytchuk.clientapi.repository.detail;

import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactInfoRepository extends JpaRepository<ContactInfo, Long> {
}
