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
        assertThat(LOST.routingKey("created")).isEqualTo("item.lost.created");
        assertThat(FOUND.routingKey("created")).isEqualTo("item.found.created");
        assertThat(LOST.routingKey("claimed")).isEqualTo("item.lost.claimed");
        assertThat(FOUND.routingKey("returned")).isEqualTo("item.found.returned");
        assertThat(LOST.routingKey("paid")).isEqualTo("item.lost.paid");
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
