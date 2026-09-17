package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.items.LostItemRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class LostItemAdminService extends AdminItemService<LostItem> {

    private final SimilarItemRepository similarItemRepository;

    public LostItemAdminService(LostItemRepository repository,
                                ThingCategoryRepository categoryRepository,
                                PlaceRepository placeRepository,
                                ContactInfoRepository contactInfoRepository,
                                SimilarItemRepository similarItemRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                LostItem::new, "Загублену річ");
        this.similarItemRepository = similarItemRepository;
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByLostItemId(id);
    }
}
