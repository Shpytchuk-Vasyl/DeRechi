package org.shpytchuk.clientapi.service;

import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.entity.LostItem;
import org.shpytchuk.clientapi.repository.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.LostItemRepository;
import org.shpytchuk.clientapi.repository.PlaceRepository;
import org.shpytchuk.clientapi.repository.ThingCategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class LostItemService extends ItemService<LostItem> {

    public LostItemService(LostItemRepository repository,
                           ThingCategoryRepository categoryRepository,
                           PlaceRepository placeRepository,
                           ContactInfoRepository contactInfoRepository,
                           CountriesProperties countries) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                LostItem::new, false, "Загублену річ");
    }
}
