package org.shpytchuk.clientapi.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClaimEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void sendsTheClaimIdToTheConfiguredExchangeUnderTheChangeRoutingKey() {
        ClaimEventPublisher publisher = new ClaimEventPublisher(rabbitTemplate, new ClaimsProperties("derechi.items"));
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);

        publisher.publish(new ClaimChange("item.found.returned", 7L));

        verify(rabbitTemplate).convertAndSend(eq("derechi.items"), eq("item.found.returned"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ClaimEvent(7L));
    }
}
