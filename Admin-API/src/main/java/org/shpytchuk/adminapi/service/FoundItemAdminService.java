package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.FoundItem;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.FoundItemRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.SimilarItemRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class FoundItemAdminService extends AdminItemService<FoundItem> {

    private final SimilarItemRepository similarItemRepository;

    public FoundItemAdminService(FoundItemRepository repository,
                                 ThingCategoryRepository categoryRepository,
                                 PlaceRepository placeRepository,
                                 ContactInfoRepository contactInfoRepository,
                                 SimilarItemRepository similarItemRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                FoundItem::new, "Знайдену річ");
        this.similarItemRepository = similarItemRepository;
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByFoundItemId(id);
    }
}
