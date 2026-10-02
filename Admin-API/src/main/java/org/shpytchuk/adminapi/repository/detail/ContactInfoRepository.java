package org.shpytchuk.adminapi.repository.detail;

import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactInfoRepository extends JpaRepository<ContactInfo, Long> {
}
