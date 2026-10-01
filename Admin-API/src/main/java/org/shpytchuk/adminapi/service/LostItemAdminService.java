package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.items.LostItemClaim;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.items.LostItemClaimRepository;
import org.shpytchuk.adminapi.repository.items.LostItemRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.ClaimView;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class LostItemAdminService extends AdminItemService<LostItem> {

    private static final String ARCHIVE_ROUTING_KEY = "item.lost.archive";

    private final SimilarItemRepository similarItemRepository;
    private final ItemClaims<LostItemClaim> itemClaims;

    public LostItemAdminService(LostItemRepository repository,
                                ThingCategoryRepository categoryRepository,
                                PlaceRepository placeRepository,
                                ContactInfoRepository contactInfoRepository,
                                CountriesProperties countries,
                                SimilarItemRepository similarItemRepository,
                                LostItemClaimRepository claimRepository,
                                RabbitTemplate rabbitTemplate,
                                ArchiveProperties archive) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                LostItem::new, "LOST_ITEM", rabbitTemplate, archive, ARCHIVE_ROUTING_KEY);
        this.similarItemRepository = similarItemRepository;
        this.itemClaims = ItemClaims.ofLive(claimRepository, contactInfoRepository);
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByLostItemId(id);
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
}
