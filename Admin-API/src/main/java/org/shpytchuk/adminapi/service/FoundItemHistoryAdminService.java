package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.items.FoundItemHistory;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemHistoryRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FoundItemHistoryAdminService extends AdminItemService<FoundItemHistory> {

    public FoundItemHistoryAdminService(FoundItemHistoryRepository repository,
                                       ThingCategoryRepository categoryRepository,
                                       PlaceRepository placeRepository,
                                       ContactInfoRepository contactInfoRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                FoundItemHistoryAdminService::archived, "FOUND_ITEM_HISTORY");
    }

    @Override
    protected void deleteMatches(Long id) {
        // no-op
    }

    @Override
    protected void archiveItem(FoundItemHistory item) {
        throw new UnsupportedOperationException("Архів уже є архівом");
    }

    private static FoundItemHistory archived() {
        FoundItemHistory item = new FoundItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
