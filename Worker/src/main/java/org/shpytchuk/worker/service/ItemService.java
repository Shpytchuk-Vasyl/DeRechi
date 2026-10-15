package org.shpytchuk.worker.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.language.SearchLanguage;
import org.shpytchuk.worker.specification.ThingSpecifications;

import java.util.List;

@AllArgsConstructor
public class ItemService<T extends Thing> {

    private static final int MAX_CANDIDATES = 5;

    private final EntityManager entityManager;
    private final Class<T> type;
    private final LanguageResolver languageResolver;

    public List<MatchCandidate> findMostSuitable(ItemCreatedEvent event) {
        SearchLanguage language = languageResolver.resolve(event.getTitle());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<MatchCandidate> query = cb.createQuery(MatchCandidate.class);
        Root<T> root = query.from(type);
        Expression<Double> rank = ThingSpecifications.matchRank(root, cb, event.getTitle(), language);

        query.select(cb.construct(MatchCandidate.class, root.get("id"), rank))
                .where(ThingSpecifications.<T>byFilter(event, language).toPredicate(root, query, cb))
                .orderBy(cb.desc(rank), cb.asc(root.get("id")));

        return entityManager.createQuery(query).setMaxResults(MAX_CANDIDATES).getResultList();
    }
}
