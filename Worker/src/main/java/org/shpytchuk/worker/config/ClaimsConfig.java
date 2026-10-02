package org.shpytchuk.worker.config;

import org.shpytchuk.worker.handler.ClaimHandler;
import org.shpytchuk.worker.handler.ClaimedHandler;
import org.shpytchuk.worker.handler.PaidHandler;
import org.shpytchuk.worker.handler.ReturnedHandler;
import org.shpytchuk.worker.service.ClaimNotifier;
import org.shpytchuk.worker.service.ClaimRepositories;
import org.shpytchuk.worker.service.ItemArchiver;
import org.shpytchuk.worker.service.ItemKind;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ClaimsProperties.class, ArchiveProperties.class})
public class ClaimsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClaimHandler lostClaimedHandler(ClaimRepositories repositories, ClaimNotifier notifier) {
        return new ClaimedHandler(ItemKind.LOST, repositories, notifier);
    }

    @Bean
    public ClaimHandler foundClaimedHandler(ClaimRepositories repositories, ClaimNotifier notifier) {
        return new ClaimedHandler(ItemKind.FOUND, repositories, notifier);
    }

    @Bean
    public ClaimHandler lostReturnedHandler(ClaimRepositories repositories, ItemArchiver archiver) {
        return new ReturnedHandler(ItemKind.LOST, repositories, archiver);
    }

    @Bean
    public ClaimHandler foundReturnedHandler(ClaimRepositories repositories, ItemArchiver archiver) {
        return new ReturnedHandler(ItemKind.FOUND, repositories, archiver);
    }

    @Bean
    public ClaimHandler lostPaidHandler(ClaimRepositories repositories, ClaimNotifier notifier, Clock clock) {
        return new PaidHandler(ItemKind.LOST, repositories, notifier, clock);
    }

    @Bean
    public ClaimHandler foundPaidHandler(ClaimRepositories repositories, ClaimNotifier notifier, Clock clock) {
        return new PaidHandler(ItemKind.FOUND, repositories, notifier, clock);
    }
}
