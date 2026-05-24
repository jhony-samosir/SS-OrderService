package com.samstore.orderservice.messaging;

import com.samstore.orderservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishEvent(String routingKey, String payloadJson) {
        log.info("Publishing event to exchange: {}, routing key: {}", RabbitMQConfig.ORDERS_EXCHANGE, routingKey);

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        
        Message message = new Message(payloadJson.getBytes(), properties);

        rabbitTemplate.send(RabbitMQConfig.ORDERS_EXCHANGE, routingKey, message);
    }
}
