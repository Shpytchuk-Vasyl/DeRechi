package org.shpytchuk.clientapi.service;

import org.shpytchuk.clientapi.entity.FoundItem;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.FoundItemRepository;
import org.shpytchuk.clientapi.repository.PlaceRepository;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class FoundItemService extends ItemService<FoundItem> {

    public FoundItemService(FoundItemRepository repository,
                            ThingCategoryRepository categoryRepository,
                            PlaceRepository placeRepository,
                            ContactInfoRepository contactInfoRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository,
                FoundItem::new, true, "Знайдену річ");
    }
}
