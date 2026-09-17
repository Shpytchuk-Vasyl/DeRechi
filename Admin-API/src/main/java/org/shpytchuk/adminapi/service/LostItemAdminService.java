package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.LostItemRepository;
import org.shpytchuk.adminapi.repository.items.LostItemHistoryRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LostItemAdminService extends AdminItemService<LostItem> {

    private final SimilarItemRepository similarItemRepository;
    private final LostItemHistoryRepository historyRepository;

    public LostItemAdminService(LostItemRepository repository,
                                ThingCategoryRepository categoryRepository,
                                PlaceRepository placeRepository,
                                ContactInfoRepository contactInfoRepository,
                                SimilarItemRepository similarItemRepository,
                                LostItemHistoryRepository historyRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                LostItem::new, "Загублену річ");
        this.similarItemRepository = similarItemRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByLostItemId(id);
    }

    @Override
    protected void archiveItem(LostItem item) {
        LostItemHistory archived = new LostItemHistory();
        archived.setArchivedAt(Instant.now());
        ItemMapper.copy(item, archived);
        historyRepository.save(archived);
    }
}
