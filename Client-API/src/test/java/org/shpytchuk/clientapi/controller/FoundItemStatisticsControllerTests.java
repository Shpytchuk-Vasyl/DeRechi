package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemHistory;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemHistoryRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

class FoundItemStatisticsControllerTests extends AbstractGraphQlTests {

    private static final String STATS = "{ stats { returnedThisWeek foundToday } }";

    @Autowired
    private FoundItemRepository foundItemRepository;

    @Autowired
    private FoundItemHistoryRepository historyRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ContactInfoRepository contactRepository;

    private Place place;
    private ContactInfo contact;

    @BeforeEach
    void seedDetails() {
        place = placeRepository.save(Fixtures.place("ChIJrynok", "Площа Ринок", 49.8419, 24.0315));
        contact = contactRepository.save(Fixtures.contact());
    }

    @Test
    void isZeroWithoutItems() {
        tester.document(STATS)
                .execute()
                .path("stats.returnedThisWeek").entity(Integer.class).isEqualTo(0)
                .path("stats.foundToday").entity(Integer.class).isEqualTo(0);
    }

    @Test
    void countsFoundItemsDatedToday() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        found(today);
        found(today);
        found(today.minusDays(1));

        tester.document(STATS)
                .execute()
                .path("stats.foundToday").entity(Integer.class).isEqualTo(2);
    }

    @Test
    void countsFoundItemsArchivedOverTheLastSevenDaysTodayIncluded() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        archived(Instant.now());
        archived(today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant());
        archived(today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1));

        tester.document(STATS)
                .execute()
                .path("stats.returnedThisWeek").entity(Integer.class).isEqualTo(2);
    }

    private void found(LocalDate date) {
        foundItemRepository.save(Fixtures.item(new FoundItem(), "Парасолька", date, documents, place, contact));
    }

    private void archived(Instant archivedAt) {
        FoundItemHistory history = Fixtures.item(
                new FoundItemHistory(), "Парасолька", LocalDate.now(), documents, place, contact);
        history.setArchivedAt(archivedAt);
        historyRepository.save(history);
    }
}
