package org.shpytchuk.automaticsearch.config;

import org.shpytchuk.automaticsearch.service.ClaimHandler;
import org.shpytchuk.automaticsearch.service.ItemKind;
import org.shpytchuk.automaticsearch.service.ClaimNotifier;
import org.shpytchuk.automaticsearch.service.ClaimRepositories;
import org.shpytchuk.automaticsearch.service.ClaimedHandler;
import org.shpytchuk.automaticsearch.service.ItemArchiver;
import org.shpytchuk.automaticsearch.service.ReturnedHandler;
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
}
