package org.shpytchuk.clientapi.config;

import org.shpytchuk.clientapi.event.ItemCreatedEvent;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration
public class RabbitConfig {

    private static final String ITEM_CREATED_TYPE_ID = "item.created";

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setJavaTypeMapper(javaTypeMapper());
        return converter;
    }


    private static JacksonJavaTypeMapper javaTypeMapper() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of(ITEM_CREATED_TYPE_ID, ItemCreatedEvent.class));
        return typeMapper;
    }
}
