package org.shpytchuk.clientapi.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.shpytchuk.clientapi.dto.ItemDto;
import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.shpytchuk.clientapi.service.LostItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(0)
public class ItemEventAspect {

    private static final Logger log = LoggerFactory.getLogger(ItemEventAspect.class);

    private static final String EXCHANGE = "derechi.items";
    private static final String LOST_CREATED_KEY = "item.lost.created";
    private static final String FOUND_CREATED_KEY = "item.found.created";

    private final RabbitTemplate rabbitTemplate;

    public ItemEventAspect(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Pointcut("execution(* org.shpytchuk.clientapi.service.ItemService+.create(..))")
    public void itemCreated() {
    }

    @AfterReturning(pointcut = "itemCreated()", returning = "created")
    public void publishItemCreated(JoinPoint joinPoint, ItemDto created) {
        String routingKey = routingKey(joinPoint.getTarget());
        ItemCreatedEvent event = new ItemCreatedEvent(created);

        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, event);
        log.debug("Published {} whit key {}", event, routingKey);
    }

    private static String routingKey(Object service) {
        return service instanceof LostItemService ? LOST_CREATED_KEY : FOUND_CREATED_KEY;
    }
}
