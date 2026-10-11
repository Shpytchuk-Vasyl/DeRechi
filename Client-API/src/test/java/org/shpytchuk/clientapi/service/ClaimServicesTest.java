package org.shpytchuk.clientapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.entity.ItemKind;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ClaimServicesTest {

    @Mock
    private LostClaimService lostClaimService;

    @Mock
    private FoundClaimService foundClaimService;

    @Test
    void findsTheServiceOfEachKind() {
        given(lostClaimService.kind()).willReturn(ItemKind.LOST);
        given(foundClaimService.kind()).willReturn(ItemKind.FOUND);

        ClaimServices services = new ClaimServices(List.of(lostClaimService, foundClaimService));

        assertThat(services.of(ItemKind.LOST)).isSameAs(lostClaimService);
        assertThat(services.of(ItemKind.FOUND)).isSameAs(foundClaimService);
    }

    @Test
    void listsTheServicesInKindOrderWhateverTheInjectionOrder() {
        given(lostClaimService.kind()).willReturn(ItemKind.LOST);
        given(foundClaimService.kind()).willReturn(ItemKind.FOUND);

        ClaimServices services = new ClaimServices(List.of(foundClaimService, lostClaimService));

        assertThat(services.all()).containsExactly(lostClaimService, foundClaimService);
    }

    @Test
    void refusesToStartWithoutAServiceForEveryKind() {
        given(lostClaimService.kind()).willReturn(ItemKind.LOST);

        assertThatThrownBy(() -> new ClaimServices(List.of(lostClaimService)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FOUND");
    }

    @Test
    void refusesTwoServicesOfOneKind() {
        given(lostClaimService.kind()).willReturn(ItemKind.LOST);
        given(foundClaimService.kind()).willReturn(ItemKind.LOST);

        assertThatThrownBy(() -> new ClaimServices(List.of(lostClaimService, foundClaimService)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOST");
    }
}
