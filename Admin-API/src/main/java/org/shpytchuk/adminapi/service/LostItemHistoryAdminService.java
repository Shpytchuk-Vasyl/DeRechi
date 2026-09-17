package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.LostItemHistoryRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LostItemHistoryAdminService extends AdminItemService<LostItemHistory> {

    public LostItemHistoryAdminService(LostItemHistoryRepository repository,
                                       ThingCategoryRepository categoryRepository,
                                       PlaceRepository placeRepository,
                                       ContactInfoRepository contactInfoRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                LostItemHistoryAdminService::archived, "Архівну загублену річ");
    }

    @Override
    protected void deleteMatches(Long id) {
        // no-op
    }

    @Override
    protected void archiveItem(LostItemHistory item) {
        throw new UnsupportedOperationException("Архів уже є архівом");
    }

    private static LostItemHistory archived() {
        LostItemHistory item = new LostItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
