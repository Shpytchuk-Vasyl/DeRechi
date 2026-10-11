package org.shpytchuk.clientapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.entity.ItemKind;
import org.shpytchuk.clientapi.exeption.NotFoundException;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @Mock
    private LostClaimService lostClaimService;

    @Mock
    private FoundClaimService foundClaimService;

    private ReturnService service;

    @BeforeEach
    void wireTheRegistry() {
        given(lostClaimService.kind()).willReturn(ItemKind.LOST);
        given(foundClaimService.kind()).willReturn(ItemKind.FOUND);
        service = new ReturnService(new ClaimServices(List.of(lostClaimService, foundClaimService)));
    }

    @Test
    void confirmsThroughTheKindThatKnowsTheToken() {
        given(lostClaimService.confirm(TOKEN)).willReturn(Optional.empty());
        given(foundClaimService.confirm(TOKEN)).willReturn(Optional.of(dto(false)));

        assertThat(service.confirm(TOKEN)).isTrue();
    }

    @Test
    void stopsAtTheFirstKindThatAnswers() {
        given(lostClaimService.confirm(TOKEN)).willReturn(Optional.of(dto(false)));

        assertThat(service.confirm(TOKEN)).isTrue();

        verify(foundClaimService, never()).confirm(any());
    }

    @Test
    void answersTrueForATokenThatWasAlreadyConfirmed() {
        given(lostClaimService.confirm(TOKEN)).willReturn(Optional.of(dto(true)));

        assertThat(service.confirm(TOKEN)).isTrue();
    }

    @Test
    void reportsAnUnknownTokenAsNotFound() {
        given(lostClaimService.confirm(TOKEN)).willReturn(Optional.empty());
        given(foundClaimService.confirm(TOKEN)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(TOKEN))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Claim.id: " + TOKEN);
    }

    private static ClaimDto dto(boolean repeated) {
        return new ClaimDto(7L, repeated, null, false, false);
    }
}
