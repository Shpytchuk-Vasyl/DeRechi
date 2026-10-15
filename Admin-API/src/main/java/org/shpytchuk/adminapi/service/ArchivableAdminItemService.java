package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.config.property.ArchiveProperties;
import org.shpytchuk.adminapi.config.property.NoticeStatusProperties;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.event.ArchiveRequestedEvent;
import org.shpytchuk.adminapi.event.ItemCreatedEvent;
import org.shpytchuk.adminapi.event.NoticeEvent;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.shpytchuk.adminapi.view.DeletedItem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

public abstract class ArchivableAdminItemService<T extends Thing> extends AdminItemService<T> {

    private static final String ARCHIVE = "archive";
    private static final String CREATED = "created";
    private static final String DELETED = "deleted";

    private final RabbitTemplate rabbitTemplate;
    private final ArchiveProperties archive;
    private final NoticeStatusProperties noticeStatus;
    private final AfterCommitPublisher publisher;

    protected ArchivableAdminItemService(ThingRepository<T> repository,
                                         ContactInfoRepository contactInfoRepository,
                                         ItemClaims<?> claims,
                                         ItemFormApplier applier,
                                         Supplier<T> factory,
                                         ItemKind kind,
                                         RabbitTemplate rabbitTemplate,
                                         ArchiveProperties archive,
                                         NoticeStatusProperties noticeStatus,
                                         AfterCommitPublisher publisher) {
        super(repository, contactInfoRepository, claims, applier, factory, kind);
        this.rabbitTemplate = rabbitTemplate;
        this.archive = archive;
        this.noticeStatus = noticeStatus;
        this.publisher = publisher;
    }

    @Override
    protected abstract int deleteMatches(Long id);

    @Override
    @Transactional
    public T create(ItemForm form) {
        T item = super.create(form);
        publisher.publish(noticeStatus.exchange(), kind().noticeKey(CREATED), ItemCreatedEvent.of(item));
        return item;
    }

    @Override
    @Transactional
    public DeletedItem delete(Long id) {
        DeletedItem deleted = super.delete(id);
        publisher.publish(noticeStatus.exchange(), kind().noticeKey(DELETED), new NoticeEvent(id));
        return deleted;
    }

    @Transactional(readOnly = true)
    public void archive(Long id, String actor) {
        require(id);
        rabbitTemplate.convertAndSend(archive.exchange(), kind().noticeKey(ARCHIVE), new ArchiveRequestedEvent(id, actor));
    }
}
