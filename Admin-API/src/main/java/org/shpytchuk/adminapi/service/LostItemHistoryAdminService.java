package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.items.LostItemClaim;
import org.shpytchuk.adminapi.entity.items.LostItemHistory;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.items.LostItemClaimRepository;
import org.shpytchuk.adminapi.repository.items.LostItemHistoryRepository;
import org.shpytchuk.adminapi.view.ClaimView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class LostItemHistoryAdminService extends AdminItemService<LostItemHistory> {

    private final ItemClaims<LostItemClaim> itemClaims;

    public LostItemHistoryAdminService(LostItemHistoryRepository repository,
                                       ThingCategoryRepository categoryRepository,
                                       PlaceRepository placeRepository,
                                       ContactInfoRepository contactInfoRepository,
                                       CountriesProperties countries,
                                       LostItemClaimRepository claimRepository) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                LostItemHistoryAdminService::archived, "LOST_ITEM_HISTORY", null, null, null);
        this.itemClaims = ItemClaims.ofArchived(claimRepository, contactInfoRepository);
    }

    @Override
    protected void deleteMatches(Long id) {
        // no-op
    }

    @Override
    protected void deleteClaims(Long id) {
        // no-op
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<ClaimView>> claims(Collection<Long> itemIds) {
        return itemClaims.byItem(itemIds);
    }

    private static LostItemHistory archived() {
        LostItemHistory item = new LostItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
