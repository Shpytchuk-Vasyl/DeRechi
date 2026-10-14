package org.shpytchuk.adminapi.repository.ingest;

import org.shpytchuk.adminapi.entity.ingest.IngestPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IngestPostRepository extends JpaRepository<IngestPost, Long> {

    List<IngestPost> findByItemKindAndItemIdInOrderByIdAsc(String itemKind, Collection<Long> itemIds);
}
