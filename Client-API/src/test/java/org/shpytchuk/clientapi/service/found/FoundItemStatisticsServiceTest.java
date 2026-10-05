package org.shpytchuk.clientapi.service.found;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.dto.FoundItemStatisticsDto;
import org.shpytchuk.clientapi.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FoundItemStatisticsServiceTest {

    @Mock
    private FoundItemHistoryRepository historyRepository;

    @Mock
    private FoundItemRepository foundItemRepository;

    @InjectMocks
    private FoundItemStatisticsService service;

    @Test
    void countsArchivedSinceMidnightUtcSixDaysAgoAndFoundItemsDatedTodayUtc() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        given(historyRepository.countByArchivedAtGreaterThanEqual(
                today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant())).willReturn(4L);
        given(foundItemRepository.countByDate(today)).willReturn(2L);

        assertThat(service.stats()).isEqualTo(new FoundItemStatisticsDto(4, 2));
    }
}
