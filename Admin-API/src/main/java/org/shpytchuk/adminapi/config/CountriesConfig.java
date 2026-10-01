package org.shpytchuk.adminapi.config;

import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CountriesProperties.class)
public class CountriesConfig {
}
