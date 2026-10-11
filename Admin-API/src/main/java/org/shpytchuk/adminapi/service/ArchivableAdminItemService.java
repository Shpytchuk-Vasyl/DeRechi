package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.event.ArchiveRequestedEvent;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

public abstract class ArchivableAdminItemService<T extends Thing> extends AdminItemService<T> {

    private final RabbitTemplate rabbitTemplate;
    private final ArchiveProperties archive;
    private final String archiveRoutingKey;

    protected ArchivableAdminItemService(ThingRepository<T> repository,
                                         ContactInfoRepository contactInfoRepository,
                                         ItemClaims<?> claims,
                                         ItemFormApplier applier,
                                         Supplier<T> factory,
                                         ItemKind kind,
                                         RabbitTemplate rabbitTemplate,
                                         ArchiveProperties archive,
                                         String archiveRoutingKey) {
        super(repository, contactInfoRepository, claims, applier, factory, kind);
        this.rabbitTemplate = rabbitTemplate;
        this.archive = archive;
        this.archiveRoutingKey = archiveRoutingKey;
    }

    @Override
    protected abstract int deleteMatches(Long id);

    @Transactional(readOnly = true)
    public void archive(Long id, String actor) {
        require(id);
        rabbitTemplate.convertAndSend(archive.exchange(), archiveRoutingKey, new ArchiveRequestedEvent(id, actor));
    }
}
