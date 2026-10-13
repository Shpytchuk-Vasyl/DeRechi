package org.shpytchuk.ingest.config.facebook;

import org.shpytchuk.ingest.source.facebook.FacebookClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FacebookConfig {

    @Bean
    public FacebookClient facebookClient(FacebookProperties properties) {
        return new FacebookClient(properties);
    }
}
