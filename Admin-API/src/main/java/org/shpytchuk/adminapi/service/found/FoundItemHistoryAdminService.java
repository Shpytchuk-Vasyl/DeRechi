package org.shpytchuk.adminapi.service.found;

import org.shpytchuk.adminapi.entity.found.FoundItemHistory;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.service.ItemClaims;
import org.shpytchuk.adminapi.service.ItemKind;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FoundItemHistoryAdminService extends AdminItemService<FoundItemHistory> {

    public FoundItemHistoryAdminService(FoundItemHistoryRepository repository,
                                        FoundItemClaimRepository claimRepository,
                                        ContactInfoRepository contactInfoRepository,
                                        ItemFormApplier applier) {
        super(repository, contactInfoRepository, ItemClaims.ofArchived(claimRepository, contactInfoRepository),
                applier, FoundItemHistoryAdminService::archived, ItemKind.FOUND_ITEM_HISTORY);
    }

    private static FoundItemHistory archived() {
        FoundItemHistory item = new FoundItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
