package org.shpytchuk.clientapi.aspect;

import lombok.AllArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.shpytchuk.clientapi.config.ClaimsProperties;
import org.shpytchuk.clientapi.dto.ClaimDto;
import org.shpytchuk.clientapi.event.ClaimEvent;
import org.shpytchuk.clientapi.event.EventPublisher;
import org.shpytchuk.clientapi.service.lost.LostClaimService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@Order(0)
@AllArgsConstructor
public class ClaimEventAspect {

    private static final String LOST_PREFIX = "item.lost";
    private static final String FOUND_PREFIX = "item.found";
    private static final String CLAIMED = ".claimed";
    private static final String RETURNED = ".returned";
    private static final String PAID = ".paid";

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

    @AfterReturning(pointcut = "claimed()", returning = "claim")
    public void publishClaimed(JoinPoint joinPoint, ClaimDto claim) {
        publish(joinPoint, claim, CLAIMED);
    }

    @AfterReturning(pointcut = "confirmed()", returning = "claim")
    public void publishReturned(JoinPoint joinPoint, Optional<ClaimDto> claim) {
        claim.ifPresent(dto -> publish(joinPoint, dto, RETURNED));
    }

    @AfterReturning(pointcut = "paid()", returning = "claim")
    public void publishPaid(JoinPoint joinPoint, Optional<ClaimDto> claim) {
        claim.ifPresent(dto -> publish(joinPoint, dto, PAID));
    }

    private void publish(JoinPoint joinPoint, ClaimDto claim, String verb) {
        if (claim.repeated()) {
            return;
        }
        publisher.publish(properties.exchange(), prefix(joinPoint.getTarget()) + verb, new ClaimEvent(claim.id()));
    }

    private static String prefix(Object service) {
        return service instanceof LostClaimService ? LOST_PREFIX : FOUND_PREFIX;
    }
}
