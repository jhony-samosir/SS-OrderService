package com.samstore.orderservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDERS_EXCHANGE = "samstore.events";
    public static final String PAYMENT_EXCHANGE = "samstore.payment";
    public static final String PAYMENT_COMPLETED_QUEUE = "orders.payment-completed-queue";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";

    public static final String CHECKOUT_INITIATED_QUEUE = "orders.checkout-initiated-queue";
    public static final String CHECKOUT_INITIATED_ROUTING_KEY = "order.checkout.initiated";

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE).build();
    }

    @Bean
    public Queue checkoutInitiatedQueue() {
        return QueueBuilder.durable(CHECKOUT_INITIATED_QUEUE).build();
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCompletedQueue)
                .to(paymentExchange)
                .with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding checkoutInitiatedBinding(Queue checkoutInitiatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(checkoutInitiatedQueue)
                .to(ordersExchange)
                .with(CHECKOUT_INITIATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
