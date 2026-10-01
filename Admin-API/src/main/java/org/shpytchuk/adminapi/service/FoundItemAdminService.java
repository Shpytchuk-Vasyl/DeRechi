package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.entity.items.FoundItem;
import org.shpytchuk.adminapi.entity.items.FoundItemClaim;
import org.shpytchuk.adminapi.repository.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.PlaceRepository;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemClaimRepository;
import org.shpytchuk.adminapi.repository.items.FoundItemRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.ClaimView;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class FoundItemAdminService extends AdminItemService<FoundItem> {

    private static final String ARCHIVE_ROUTING_KEY = "item.found.archive";

    private final SimilarItemRepository similarItemRepository;
    private final ItemClaims<FoundItemClaim> itemClaims;

    public FoundItemAdminService(FoundItemRepository repository,
                                 ThingCategoryRepository categoryRepository,
                                 PlaceRepository placeRepository,
                                 ContactInfoRepository contactInfoRepository,
                                 CountriesProperties countries,
                                 SimilarItemRepository similarItemRepository,
                                 FoundItemClaimRepository claimRepository,
                                 RabbitTemplate rabbitTemplate,
                                 ArchiveProperties archive) {
        super(repository, categoryRepository, placeRepository, contactInfoRepository, countries,
                FoundItem::new, "FOUND_ITEM", rabbitTemplate, archive, ARCHIVE_ROUTING_KEY);
        this.similarItemRepository = similarItemRepository;
        this.itemClaims = ItemClaims.ofLive(claimRepository, contactInfoRepository);
    }

    @Override
    protected void deleteMatches(Long id) {
        similarItemRepository.deleteByFoundItemId(id);
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
