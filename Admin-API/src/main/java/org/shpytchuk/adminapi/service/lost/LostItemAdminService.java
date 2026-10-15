package org.shpytchuk.adminapi.service.lost;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.NoticeStatusProperties;
import org.shpytchuk.adminapi.entity.lost.LostItem;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.adminapi.repository.lost.LostItemRepository;
import org.shpytchuk.adminapi.repository.matching.SimilarItemRepository;
import org.shpytchuk.adminapi.service.AfterCommitPublisher;
import org.shpytchuk.adminapi.service.ArchivableAdminItemService;
import org.shpytchuk.adminapi.service.ItemClaims;
import org.shpytchuk.adminapi.service.ItemKind;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class LostItemAdminService extends ArchivableAdminItemService<LostItem> {

    private final SimilarItemRepository similarItemRepository;

    public LostItemAdminService(LostItemRepository repository,
                                LostItemClaimRepository claimRepository,
                                ContactInfoRepository contactInfoRepository,
                                ItemFormApplier applier,
                                SimilarItemRepository similarItemRepository,
                                RabbitTemplate rabbitTemplate,
                                ArchiveProperties archive,
                                NoticeStatusProperties noticeStatus,
                                AfterCommitPublisher publisher) {
        super(repository, contactInfoRepository, ItemClaims.ofLive(claimRepository, contactInfoRepository),
                applier, LostItem::new, ItemKind.LOST_ITEM, rabbitTemplate, archive, noticeStatus, publisher);
        this.similarItemRepository = similarItemRepository;
    }

    @Override
    protected int deleteMatches(Long id) {
        return similarItemRepository.deleteByLostItemId(id);
    }
}
