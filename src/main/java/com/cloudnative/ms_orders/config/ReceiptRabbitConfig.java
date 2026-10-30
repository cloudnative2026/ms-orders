package com.cloudnative.ms_orders.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReceiptRabbitConfig {
    public static final String EXCHANGE = "receipts.exchange";
    public static final String QUEUE = "receipts.queue";
    public static final String KEY = "receipts.generate";
    @Bean public Declarables receiptTopology() {
        var exchange = new DirectExchange(EXCHANGE);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(EXCHANGE + ".dlx")
            .deadLetterRoutingKey("failed").build();
        var dlx = new DirectExchange(EXCHANGE + ".dlx");
        var dlq = QueueBuilder.durable(QUEUE + ".dlq").build();
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange).with(KEY),
            dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with("failed"));
    }
}
