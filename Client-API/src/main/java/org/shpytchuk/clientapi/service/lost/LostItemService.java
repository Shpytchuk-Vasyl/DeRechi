package org.shpytchuk.clientapi.service.lost;

import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.service.ItemService;
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
