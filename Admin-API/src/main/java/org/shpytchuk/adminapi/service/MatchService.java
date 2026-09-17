package org.shpytchuk.adminapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.items.LostItemRepository;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.CandidateView;
import org.shpytchuk.adminapi.view.MatchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class MatchService {

    private final LostItemRepository lostItemRepository;
    private final SimilarItemRepository similarItemRepository;

    @Transactional(readOnly = true)
    public Page<MatchRow> page(int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Order.desc("id")));
        Page<LostItem> lostItems = lostItemRepository.findAll(pageRequest);

        Map<Long, List<CandidateView>> candidates = candidatesByLostItem(lostItems.getContent());

        return lostItems.map(lost -> new MatchRow(
                ItemMapper.toView(lost),
                candidates.getOrDefault(lost.getId(), List.of())));
    }

    private Map<Long, List<CandidateView>> candidatesByLostItem(List<LostItem> lostItems) {
        if (lostItems.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = lostItems.stream().map(LostItem::getId).toList();

        Map<Long, List<CandidateView>> grouped = new LinkedHashMap<>();
        for (SimilarItem similar : similarItemRepository.findByLostItemIdInOrderByMatchOrderDesc(ids)) {
            grouped.computeIfAbsent(similar.getId().getLostItemId(), key -> new java.util.ArrayList<>())
                    .add(toCandidate(similar));
        }
        return grouped;
    }

    private static CandidateView toCandidate(SimilarItem similar) {
        return new CandidateView(
                ItemMapper.toView(similar.getFoundItem()),
                similar.getMatchOrder(),
                similar.getNotifiedAt(),
                similar.getNotifiedBy());
    }
}
