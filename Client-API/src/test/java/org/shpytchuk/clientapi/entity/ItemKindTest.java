package org.shpytchuk.clientapi.entity;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.found.FoundItemHistory;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.shpytchuk.clientapi.entity.ItemKind.FOUND;
import static org.shpytchuk.clientapi.entity.ItemKind.LOST;

class ItemKindTest {

    @Test
    void buildsTheRoutingKeysWorkerIsBoundTo() {
        assertThat(LOST.noticeKey("created")).isEqualTo("notice.lost.created");
        assertThat(FOUND.noticeKey("created")).isEqualTo("notice.found.created");
        assertThat(FOUND.noticeKey("returned")).isEqualTo("notice.found.returned");
        assertThat(LOST.claimKey("claimed")).isEqualTo("claim.lost.claimed");
        assertThat(LOST.claimKey("paid")).isEqualTo("claim.lost.paid");
    }

    @Test
    void labelsAreTheLowerCaseWordsUsedInUrlsAndLogs() {
        assertThat(LOST.label()).isEqualTo("lost");
        assertThat(FOUND.label()).isEqualTo("found");
    }

    @Test
    void namesTheEntitiesAsTheNotFoundMessagesDo() {
        assertThat(LOST.entityName()).isEqualTo("LostItem");
        assertThat(FOUND.entityName()).isEqualTo("FoundItem");
        assertThat(LOST.claimEntityName()).isEqualTo("LostItemClaim");
        assertThat(FOUND.claimEntityName()).isEqualTo("FoundItemClaim");
    }

    @Test
    void everyEntityKnowsItsKind() {
        assertThat(new LostItem().kind()).isEqualTo(LOST);
        assertThat(new FoundItem().kind()).isEqualTo(FOUND);
        assertThat(new FoundItemHistory().kind()).isEqualTo(FOUND);
        assertThat(new LostItemClaim().kind()).isEqualTo(LOST);
        assertThat(new FoundItemClaim().kind()).isEqualTo(FOUND);
    }
}
