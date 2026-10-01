package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.items.FoundItem;
import org.shpytchuk.adminapi.entity.items.FoundItemHistory;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemHistoryRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FoundItemAdminService extends AdminItemService<FoundItem> {

    private final SimilarItemRepository similarItemRepository;
    private final FoundItemHistoryRepository historyRepository;

    public FoundItemAdminService(FoundItemRepository repository,
                                 ThingCategoryRepository categoryRepository,
                                 PlaceRepository placeRepository,
                                 ContactInfoRepository contactInfoRepository,
                                 CountriesProperties countries,
                                 SimilarItemRepository similarItemRepository,
                                FoundItemHistoryRepository historyRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                FoundItem::new, "FOUND_ITEM");
        this.similarItemRepository = similarItemRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByFoundItemId(id);
    }

    @Override
    protected void archiveItem(FoundItem item) {
        FoundItemHistory archived = new FoundItemHistory();
        archived.setArchivedAt(Instant.now());
        ItemMapper.copy(item, archived);
        historyRepository.save(archived);
    }
}
