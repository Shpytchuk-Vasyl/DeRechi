package org.shpytchuk.clientapi.aspect;

import lombok.AllArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.event.EventPublisher;
import org.shpytchuk.clientapi.event.Exchanges;
import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.shpytchuk.clientapi.service.ItemService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
@AllArgsConstructor
public class ItemEventAspect {

    private static final String CREATED = "created";

    private final EventPublisher publisher;

    @Pointcut("execution(* org.shpytchuk.clientapi.service.ItemService+.create(..))")
    public void itemCreated() {
    }

    @AfterReturning(pointcut = "itemCreated() && target(service)", returning = "created")
    public void publishItemCreated(ItemService<?> service, ItemDto created) {
        publisher.publish(Exchanges.NOTICE_STATUS, service.kind().noticeKey(CREATED), new ItemCreatedEvent(created));
    }
}
