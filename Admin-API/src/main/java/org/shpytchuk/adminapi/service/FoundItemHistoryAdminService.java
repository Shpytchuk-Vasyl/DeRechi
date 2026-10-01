package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.items.FoundItemClaim;
import org.shpytchuk.adminapi.entity.items.FoundItemHistory;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemClaimRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemHistoryRepository;
import org.shpytchuk.adminapi.view.ClaimView;
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
    protected void deleteMatches(Long id) {
        // no-op
    }

    @Override
    protected void deleteClaims(Long id) {
        itemClaims.deleteOf(id);
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
