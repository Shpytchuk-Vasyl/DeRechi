package org.shpytchuk.clientapi.service.found;

import lombok.AllArgsConstructor;
import org.shpytchuk.clientapi.dto.FoundItemStatisticsDto;
import org.shpytchuk.clientapi.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@AllArgsConstructor
public class FoundItemStatisticsService {

    static final int WEEK_DAYS = 7;

    private final FoundItemHistoryRepository historyRepository;
    private final FoundItemRepository foundItemRepository;

    @Transactional(readOnly = true)
    public FoundItemStatisticsDto stats() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate weekStart = today.minusDays(WEEK_DAYS - 1);
        return new FoundItemStatisticsDto(
                historyRepository.countByArchivedAtGreaterThanEqual(weekStart.atStartOfDay(ZoneOffset.UTC).toInstant()),
                foundItemRepository.countByDate(today));
    }

}
