package org.shpytchuk.adminapi.service;

import org.shpytchuk.adminapi.entity.detail.ContactInfo;
import org.shpytchuk.adminapi.entity.thing.Thing;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.mapper.ItemFormApplier;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.adminapi.repository.thing.ThingRepository;
import org.shpytchuk.adminapi.specification.ThingSpecifications;
import org.shpytchuk.adminapi.view.DeletedItem;
import org.shpytchuk.adminapi.view.detail.ItemView;
import org.shpytchuk.adminapi.view.matching.ClaimView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class AdminItemService<T extends Thing> {

    private final ThingRepository<T> repository;
    private final ContactInfoRepository contactInfoRepository;
    private final ItemClaims<?> claims;
    private final ItemFormApplier applier;
    private final Supplier<T> factory;
    private final ItemKind kind;

    protected AdminItemService(ThingRepository<T> repository,
                               ContactInfoRepository contactInfoRepository,
                               ItemClaims<?> claims,
                               ItemFormApplier applier,
                               Supplier<T> factory,
                               ItemKind kind) {
        this.repository = repository;
        this.contactInfoRepository = contactInfoRepository;
        this.claims = claims;
        this.applier = applier;
        this.factory = factory;
        this.kind = kind;
    }

    protected int deleteMatches(Long id) {
        return 0;
    }

    @Transactional(readOnly = true)
    public Page<ItemView> page(Pageable pageable, ItemFilter filter) {
        return repository.findAll(ThingSpecifications.matching(filter), Pages.safe(pageable)).map(ItemMapper::toView);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ClaimView>> claims(Collection<Long> itemIds) {
        return claims.byItem(itemIds);
    }

    @Transactional(readOnly = true)
    public ItemForm form(Long id) {
        return ItemMapper.toForm(require(id));
    }

    @Transactional
    public T create(ItemForm form) {
        T item = factory.get();
        ContactInfo info = new ContactInfo();
        applier.apply(form, item, info);
        contactInfoRepository.save(info);
        return repository.save(item);
    }

    @Transactional
    public T update(Long id, ItemForm form) {
        T item = require(id);
        applier.apply(form, item, item.getInfo());
        return repository.save(item);
    }

    @Transactional
    public DeletedItem delete(Long id) {
        T item = require(id);
        int matches = deleteMatches(id);
        int deletedClaims = claims.deleteOf(id);
        repository.delete(item);
        return new DeletedItem(matches, deletedClaims);
    }

    protected T require(Long id) {
        return repository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException(kind.entityKey(), id));
    }
}
