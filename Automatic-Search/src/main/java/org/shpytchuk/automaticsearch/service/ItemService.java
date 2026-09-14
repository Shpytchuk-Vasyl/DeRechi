package org.shpytchuk.automaticsearch.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.automaticsearch.entity.Thing;
import org.shpytchuk.automaticsearch.event.ItemCreatedEvent;
import org.shpytchuk.automaticsearch.language.LanguageResolver;
import org.shpytchuk.automaticsearch.language.SearchLanguage;
import org.shpytchuk.automaticsearch.repository.ThingRepository;
import org.shpytchuk.automaticsearch.specification.ThingSpecifications;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Window;

import java.util.List;

@AllArgsConstructor
public class ItemService<T extends Thing> {

    private static final int PAGE_SIZE = 20;
    private static final int PAGE_NUMBER = 0;
    private final ThingRepository<T> repository;
    private final LanguageResolver languageResolver;


    public Window<T> findAllMostSuitable(ItemCreatedEvent event) {
        SearchLanguage language = languageResolver.resolve(event.getTitle());

        PageRequest pageRequest = PageRequest.of(PAGE_NUMBER, PAGE_SIZE);
        Page<T> result = repository.findAll(ThingSpecifications.byFilter(event, language), pageRequest);
        List<T> content = result.getContent();
        return Window.from(content, OffsetScrollPosition.positionFunction(pageRequest.getOffset()), result.hasNext());
    }
}
