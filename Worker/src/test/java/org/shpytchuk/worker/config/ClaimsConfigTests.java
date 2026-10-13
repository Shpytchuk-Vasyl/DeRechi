package org.shpytchuk.worker.config;

import org.junit.jupiter.api.Test;
import org.shpytchuk.worker.event.ClaimEvent;
import org.shpytchuk.worker.handler.ClaimHandler;
import org.shpytchuk.worker.handler.ClaimedHandler;
import org.shpytchuk.worker.handler.PaidHandler;
import org.shpytchuk.worker.listener.ClaimListener;
import org.shpytchuk.worker.repository.lost.LostItemClaimRepository;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKinds;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ClaimsConfig.ClaimHandlers} registers the handlers programmatically. This pins what that has to
 * buy: a bean per kind and verb, all of them collected by the listener, and the transactional proxy on the
 * handler that needs one.
 */
class ClaimsConfigTests {

    private static final Set<String> ROUTING_KEYS = Set.of(
            "item.lost.claimed", "item.found.claimed",
            "item.lost.returned", "item.found.returned",
            "item.lost.paid", "item.found.paid");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClaimsConfig.class, ClaimListener.class, Collaborators.class)
            .withPropertyValues(
                    "derechi.claims.queue=worker.claims",
                    "derechi.claims.exchange=derechi.notifications",
                    "derechi.claims.site-url=http://localhost:3000",
                    "derechi.claims.check-every=PT10M",
                    "derechi.claims.author-reminder-after=P1D",
                    "derechi.claims.claimant-reminder-after=P1D",
                    "derechi.claims.archive-after=P7D",
                    "derechi.claims.retention=P365D",
                    "derechi.archive.queue=worker.archive",
                    "derechi.archive.max-age=P30D");

    @Test
    void registersOneHandlerBeanPerKindAndVerb() {
        runner.run(context -> {
            Map<String, ClaimHandler> handlers = context.getBeansOfType(ClaimHandler.class);

            assertThat(handlers).containsOnlyKeys("lostClaimedHandler", "foundClaimedHandler",
                    "lostReturnedHandler", "foundReturnedHandler", "lostPaidHandler", "foundPaidHandler");
            assertThat(handlers.values()).extracting(ClaimHandler::routingKey)
                    .containsExactlyInAnyOrderElementsOf(ROUTING_KEYS);
        });
    }

    @Test
    void theListenerCollectsEveryHandler() {
        runner.run(context -> {
            ClaimListener listener = context.getBean(ClaimListener.class);

            for (String routingKey : ROUTING_KEYS) {
                assertThatCode(() -> listener.onClaim(event(), message(routingKey))).as(routingKey)
                        .doesNotThrowAnyException();
            }
            assertThatThrownBy(() -> listener.onClaim(event(), message("item.lost.archive")))
                    .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        });
    }

    @Test
    void aHandlerWithTransactionalStillGetsItsProxy() {
        runner.run(context -> {
            Object paid = context.getBean("lostPaidHandler");
            Object claimed = context.getBean("lostClaimedHandler");

            // Boot proxies with CGLIB, this bare context with JDK proxies: ask for the target class, not the type.
            assertThat(AopUtils.isAopProxy(paid)).as("PaidHandler.handle is @Transactional").isTrue();
            assertThat(AopUtils.getTargetClass(paid)).isEqualTo(PaidHandler.class);
            assertThat(claimed).isInstanceOf(ClaimedHandler.class);
            assertThat(AopUtils.isAopProxy(claimed)).as("ClaimedHandler has nothing to proxy").isFalse();
        });
    }

    private static ClaimEvent event() {
        ClaimEvent event = new ClaimEvent();
        event.setId(1L);
        return event;
    }

    private static Message message(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(new byte[0], properties);
    }

    @Configuration
    @EnableTransactionManagement
    static class Collaborators {

        @Bean
        PlatformTransactionManager transactionManager() {
            PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return transactions;
        }

        /** Every claim is gone, so each handler logs and returns: enough to see that a handler was found. */
        @Bean
        ItemKinds itemKinds() {
            ItemKinds kinds = mock(ItemKinds.class);
            doReturn(mock(LostItemClaimRepository.class)).when(kinds).claims(any());
            return kinds;
        }

        @Bean
        ClaimNotifier claimNotifier() {
            return mock(ClaimNotifier.class);
        }

        @Bean
        ItemArchiver itemArchiver() {
            return mock(ItemArchiver.class);
        }
    }
}
