package org.shpytchuk.worker.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.language.SearchLanguage;
import org.shpytchuk.worker.repository.ThingRepository;
import org.shpytchuk.worker.specification.ThingSpecifications;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Window;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.IntStream;

@AllArgsConstructor
public class ItemService<T extends Thing> {

    private static final int PAGE_SIZE = 5;
    private static final int PAGE_NUMBER = 0;
    private final ThingRepository<T> repository;
    private final LanguageResolver languageResolver;


    public Window<T> findAllMostSuitable(ItemCreatedEvent event) {
        SearchLanguage language = languageResolver.resolve(event.getTitle());

        PageRequest pageRequest = PageRequest.of(PAGE_NUMBER, PAGE_SIZE);
        Page<T> result = repository.findAll(ThingSpecifications.byFilter(event, language), pageRequest);
        List<T> content = result.getContent();

        seedContentWithRank(content, event.getTitle(), language);


        return Window.from(content, OffsetScrollPosition.positionFunction(pageRequest.getOffset()), result.hasNext());
    }


    private void seedContentWithRank(List<T> content, String title, SearchLanguage language) {
        if (StringUtils.hasText(title) && !content.isEmpty()) {
            List<Double> ranks = repository.rankAll(
                    content.stream().map(Thing::getId).toList(), language.regconfig(), title);

            IntStream.range(0, content.size())
                    .forEach(i -> content.get(i).setOrderMatch(ranks.get(i)));

        } else {
            IntStream.range(0, content.size())
                    .forEach(i -> content.get(i).setOrderMatch(0.0));
        }
    }
}
