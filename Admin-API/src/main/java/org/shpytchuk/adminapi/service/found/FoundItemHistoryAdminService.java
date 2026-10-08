package org.shpytchuk.adminapi.service.found;

import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.found.FoundItemClaim;
import org.shpytchuk.adminapi.entity.found.FoundItemHistory;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.detail.PlaceRepository;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.service.ItemClaims;
import org.shpytchuk.adminapi.view.matching.ClaimView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class FoundItemHistoryAdminService extends AdminItemService<FoundItemHistory> {

    private final ItemClaims<FoundItemClaim> itemClaims;

    public FoundItemHistoryAdminService(FoundItemHistoryRepository repository,
                                        ThingCategoryRepository categoryRepository,
                                        PlaceRepository placeRepository,
                                        ContactInfoRepository contactInfoRepository,
                                        CountriesProperties countries,
                                        FoundItemClaimRepository claimRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                FoundItemHistoryAdminService::archived, "FOUND_ITEM_HISTORY", null, null, null);
        this.itemClaims = ItemClaims.ofArchived(claimRepository, contactInfoRepository);
    }

    @Override
    protected int deleteMatches(Long id) {
        return 0;
    }

    @Override
    protected int deleteClaims(Long id) {
        return itemClaims.deleteOf(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<ClaimView>> claims(Collection<Long> itemIds) {
        return itemClaims.byItem(itemIds);
    }

    private static FoundItemHistory archived() {
        FoundItemHistory item = new FoundItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
