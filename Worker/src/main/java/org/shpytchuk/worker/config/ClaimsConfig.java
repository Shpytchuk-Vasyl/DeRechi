package org.shpytchuk.worker.config;

import org.shpytchuk.worker.handler.ClaimedHandler;
import org.shpytchuk.worker.handler.PaidHandler;
import org.shpytchuk.worker.handler.ReturnedHandler;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemKinds;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ClaimsProperties.class, ArchiveProperties.class})
@Import(ClaimsConfig.ClaimHandlers.class)
public class ClaimsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    static class ClaimHandlers implements BeanRegistrar {

        @Override
        public void register(BeanRegistry registry, Environment env) {
            for (ItemKind kind : ItemKind.values()) {
                String prefix = kind.segment();
                registry.registerBean(prefix + "ClaimedHandler", ClaimedHandler.class, spec -> spec.supplier(
                        ctx -> new ClaimedHandler(kind, ctx.bean(ItemKinds.class), ctx.bean(ClaimNotifier.class))));
                registry.registerBean(prefix + "ReturnedHandler", ReturnedHandler.class, spec -> spec.supplier(
                        ctx -> new ReturnedHandler(kind, ctx.bean(ItemKinds.class), ctx.bean(ItemArchiver.class))));
                registry.registerBean(prefix + "PaidHandler", PaidHandler.class, spec -> spec.supplier(
                        ctx -> new PaidHandler(kind, ctx.bean(ItemKinds.class), ctx.bean(ClaimNotifier.class),
                                ctx.bean(Clock.class))));
            }
        }
    }
}
