package org.shpytchuk.clientapi.aspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.event.ClaimEvent;
import org.shpytchuk.clientapi.input.ContactInfoInput;
import org.shpytchuk.clientapi.service.found.FoundClaimService;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClaimEventAspectTest {

    private static final String EXCHANGE = "derechi.items";
    private static final ContactInfoInput CONTACT = new ContactInfoInput("+48501234567", "claimant@example.com", List.of());

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private LostClaimService lostClaimService;

    @Mock
    private FoundClaimService foundClaimService;

    @Test
    void publishesANewLostClaimUnderItsOwnRoutingKey() {
        given(lostClaimService.claim(1L, CONTACT)).willReturn(new ClaimDto(7L, false));
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);

        proxy(lostClaimService).claim(1L, CONTACT);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.lost.claimed"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ClaimEvent(7L));
    }

    @Test
    void publishesANewFoundClaimUnderItsOwnRoutingKey() {
        given(foundClaimService.claim(2L, CONTACT)).willReturn(new ClaimDto(8L, false));

        proxy(foundClaimService).claim(2L, CONTACT);

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.found.claimed"), any(ClaimEvent.class));
    }

    @Test
    void staysSilentOnARepeatedClaim() {
        given(lostClaimService.claim(1L, CONTACT)).willReturn(new ClaimDto(7L, true));

        proxy(lostClaimService).claim(1L, CONTACT);

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void publishesAFreshConfirmationAsReturned() {
        given(foundClaimService.confirm("token")).willReturn(Optional.of(new ClaimDto(8L, false)));

        proxy(foundClaimService).confirm("token");

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("item.found.returned"), eq(new ClaimEvent(8L)));
    }

    @Test
    void staysSilentWhenTheTokenIsUnknownHereOrAlreadyConfirmed() {
        given(lostClaimService.confirm("missing")).willReturn(Optional.empty());
        given(lostClaimService.confirm("done")).willReturn(Optional.of(new ClaimDto(7L, true)));

        LostClaimService proxy = proxy(lostClaimService);
        proxy.confirm("missing");
        proxy.confirm("done");

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void doesNotPublishWhenTheServiceThrows() {
        given(lostClaimService.claim(1L, CONTACT)).willThrow(new IllegalArgumentException("boom"));

        LostClaimService proxy = proxy(lostClaimService);
        try {
            proxy.claim(1L, CONTACT);
        } catch (IllegalArgumentException expected) {
        }

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ClaimEventAspect(rabbitTemplate, new ClaimsProperties(EXCHANGE)));
        return factory.getProxy();
    }
}
