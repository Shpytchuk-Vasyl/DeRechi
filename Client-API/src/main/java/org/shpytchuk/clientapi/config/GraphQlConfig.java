package org.shpytchuk.clientapi.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.execution.instrumentation.Instrumentation;


@Configuration
public class GraphQlConfig {

    @Bean
    public RuntimeWiringConfigurer dateScalarConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.Date);
    }

    @Bean
    public Instrumentation maxDepth() {
        return new MaxQueryDepthInstrumentation(10);
    }

    @Bean
    public Instrumentation maxComplexity() {
        return new MaxQueryComplexityInstrumentation(200);
    }

}
