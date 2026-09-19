package org.shpytchuk.adminapi.service;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.entity.items.LostItem;
import org.shpytchuk.adminapi.entity.items.SimilarItem;
import org.shpytchuk.adminapi.exception.NotFoundException;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.mapper.ItemMapper;
import org.shpytchuk.adminapi.repository.items.LostItemRepository;
import org.shpytchuk.adminapi.repository.ThingSpecifications;
import org.shpytchuk.adminapi.repository.items.SimilarItemRepository;
import org.shpytchuk.adminapi.view.CandidateCount;
import org.shpytchuk.adminapi.view.CandidateView;
import org.shpytchuk.adminapi.view.MatchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MatchService {

    public static final int PREVIEW_SIZE = 3;

    private final LostItemRepository lostItemRepository;
    private final SimilarItemRepository similarItemRepository;

    @Transactional(readOnly = true)
    public Page<MatchRow> page(Pageable pageable, ItemFilter filter) {
        Page<LostItem> lostItems = lostItemRepository.findAll(ThingSpecifications.matching(filter), Pages.safe(pageable));

        List<Long> ids = lostItems.getContent().stream().map(LostItem::getId).toList();
        Map<Long, List<CandidateView>> preview = previewByLostItem(ids);
        Map<Long, Long> totals = totalsByLostItem(ids);

        return lostItems.map(lost -> new MatchRow(
                ItemMapper.toView(lost),
                preview.getOrDefault(lost.getId(), List.of()),
                totals.getOrDefault(lost.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public MatchRow rowWithAllCandidates(Long lostItemId) {
        LostItem lost = lostItemRepository.findById(lostItemId)
                .orElseThrow(() -> new NotFoundException("entity.LOST_ITEM", lostItemId));

        List<CandidateView> candidates = similarItemRepository
                .findByLostItemIdOrderByMatchOrderDescFoundItemIdAsc(lostItemId).stream()
                .map(MatchService::toCandidate)
                .toList();

        return new MatchRow(ItemMapper.toView(lost), candidates, candidates.size());
    }

    private Map<Long, List<CandidateView>> previewByLostItem(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<CandidateView>> grouped = new LinkedHashMap<>();
        for (SimilarItem similar : similarItemRepository.findTopByLostItemIdIn(ids, PREVIEW_SIZE)) {
            grouped.computeIfAbsent(similar.getId().getLostItemId(), key -> new ArrayList<>())
                    .add(toCandidate(similar));
        }
        return grouped;
    }

    private Map<Long, Long> totalsByLostItem(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return similarItemRepository.countByLostItemIdIn(ids).stream()
                .collect(Collectors.toMap(CandidateCount::lostItemId, CandidateCount::total));
    }

    private static CandidateView toCandidate(SimilarItem similar) {
        return new CandidateView(
                ItemMapper.toView(similar.getFoundItem()),
                similar.getMatchOrder(),
                similar.getNotifiedAt(),
                similar.getNotifiedBy());
    }
}
