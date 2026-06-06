package com.example.demo.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 如需启用 RabbitMQ，取消 @Configuration 注释
// @Configuration
public class RabbitConfig {

    public static final String QUEUE_NAME = "user.register.queue";
    public static final String EXCHANGE_NAME = "user.exchange";
    public static final String ROUTING_KEY = "user.register";

    // @Bean
    public Queue userRegisterQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    // @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    // @Bean
    public Binding binding(Queue userRegisterQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userRegisterQueue)
                .to(userExchange)
                .with(ROUTING_KEY);
    }
}
