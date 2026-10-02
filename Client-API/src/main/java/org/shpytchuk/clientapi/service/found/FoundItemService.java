package org.shpytchuk.clientapi.service.found;

import org.shpytchuk.clientapi.config.CountriesProperties;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.clientapi.service.ItemService;
import org.springframework.stereotype.Service;

@Service
public class FoundItemService extends ItemService<FoundItem> {

    public FoundItemService(FoundItemRepository repository,
                            ThingCategoryRepository categoryRepository,
                            PlaceRepository placeRepository,
                            ContactInfoRepository contactInfoRepository,
                            CountriesProperties countries) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                FoundItem::new, true, "Знайдену річ");
    }
}
