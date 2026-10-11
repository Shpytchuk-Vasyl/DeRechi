package org.shpytchuk.clientapi.aspect;

import lombok.AllArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.event.ClaimEvent;
import org.shpytchuk.clientapi.event.EventPublisher;
import org.shpytchuk.clientapi.service.ClaimService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@Order(0)
@AllArgsConstructor
public class ClaimEventAspect {

    private static final String CLAIMED = "claimed";
    private static final String RETURNED = "returned";
    private static final String PAID = "paid";

    private final EventPublisher publisher;
    private final ClaimsProperties properties;

    @Pointcut("execution(* org.shpytchuk.clientapi.service.ClaimService+.claim(..))")
    public void claimed() {
    }

    @Pointcut("execution(* org.shpytchuk.clientapi.service.ClaimService+.confirm(..))")
    public void confirmed() {
    }

    @Pointcut("execution(* org.shpytchuk.clientapi.service.ClaimService+.markPaid(..))")
    public void paid() {
    }

    @AfterReturning(pointcut = "claimed() && target(service)", returning = "claim")
    public void publishClaimed(ClaimService<?, ?> service, ClaimDto claim) {
        publish(service, claim, CLAIMED);
    }

    @AfterReturning(pointcut = "confirmed() && target(service)", returning = "claim")
    public void publishReturned(ClaimService<?, ?> service, Optional<ClaimDto> claim) {
        claim.ifPresent(dto -> publish(service, dto, RETURNED));
    }

    @AfterReturning(pointcut = "paid() && target(service)", returning = "claim")
    public void publishPaid(ClaimService<?, ?> service, Optional<ClaimDto> claim) {
        claim.ifPresent(dto -> publish(service, dto, PAID));
    }

    private void publish(ClaimService<?, ?> service, ClaimDto claim, String verb) {
        if (claim.repeated()) {
            return;
        }
        publisher.publish(properties.exchange(), service.kind().routingKey(verb), new ClaimEvent(claim.id()));
    }
}
