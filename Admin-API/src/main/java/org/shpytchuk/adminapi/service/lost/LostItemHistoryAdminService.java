package org.shpytchuk.adminapi.service.lost;

import org.shpytchuk.adminapi.entity.lost.LostItemHistory;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemHistoryRepository;
import org.shpytchuk.adminapi.service.AdminItemService;
import org.shpytchuk.adminapi.service.ItemClaims;
import org.shpytchuk.adminapi.service.ItemKind;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LostItemHistoryAdminService extends AdminItemService<LostItemHistory> {

    public LostItemHistoryAdminService(LostItemHistoryRepository repository,
                                       LostItemClaimRepository claimRepository,
                                       ContactInfoRepository contactInfoRepository,
                                       ItemFormApplier applier) {
        super(repository, contactInfoRepository, ItemClaims.ofArchived(claimRepository, contactInfoRepository),
                applier, LostItemHistoryAdminService::archived, ItemKind.LOST_ITEM_HISTORY);
    }

    private static LostItemHistory archived() {
        LostItemHistory item = new LostItemHistory();
        item.setArchivedAt(Instant.now());
        return item;
    }
}
