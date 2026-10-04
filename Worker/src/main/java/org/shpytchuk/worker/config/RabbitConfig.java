package org.shpytchuk.worker.config;

import org.aopalliance.aop.Advice;
import org.shpytchuk.worker.event.EventTypeScanner;
import org.shpytchuk.worker.logging.RabbitMessageLogger;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setJavaTypeMapper(javaTypeMapper());
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        Advice[] configured = factory.getAdviceChain() == null ? new Advice[0] : factory.getAdviceChain();
        factory.setAdviceChain(Stream.concat(Stream.of(new RabbitMessageLogger()), Stream.of(configured))
                .toArray(Advice[]::new));
        return factory;
    }

    private static JacksonJavaTypeMapper javaTypeMapper() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(EventTypeScanner.scan());
        return typeMapper;
    }
}
