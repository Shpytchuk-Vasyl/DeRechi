package org.shpytchuk.adminapi.service.found;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.entity.found.FoundItem;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.adminapi.repository.found.FoundItemRepository;
import org.shpytchuk.adminapi.repository.matching.SimilarItemRepository;
import org.shpytchuk.adminapi.service.ArchivableAdminItemService;
import org.shpytchuk.adminapi.service.ItemClaims;
import org.shpytchuk.adminapi.service.ItemKind;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class FoundItemAdminService extends ArchivableAdminItemService<FoundItem> {

    private static final String ARCHIVE_ROUTING_KEY = "item.found.archive";

    private final SimilarItemRepository similarItemRepository;

    public FoundItemAdminService(FoundItemRepository repository,
                                 FoundItemClaimRepository claimRepository,
                                 ContactInfoRepository contactInfoRepository,
                                 ItemFormApplier applier,
                                 SimilarItemRepository similarItemRepository,
                                 RabbitTemplate rabbitTemplate,
                                 ArchiveProperties archive) {
        super(repository, contactInfoRepository, ItemClaims.ofLive(claimRepository, contactInfoRepository),
                applier, FoundItem::new, ItemKind.FOUND_ITEM, rabbitTemplate, archive, ARCHIVE_ROUTING_KEY);
        this.similarItemRepository = similarItemRepository;
    }

    @Override
    protected int deleteMatches(Long id) {
        return similarItemRepository.deleteByFoundItemId(id);
    }
}
