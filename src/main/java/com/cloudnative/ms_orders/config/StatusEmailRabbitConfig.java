package com.cloudnative.ms_orders.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
@Configuration
public class StatusEmailRabbitConfig {
    public static final String EXCHANGE="orders.status.exchange", QUEUE="orders.status.email.queue", KEY="status.changed";
    @Bean public Declarables statusEmailTopology() {
        var ex=new DirectExchange(EXCHANGE);
        var q=QueueBuilder.durable(QUEUE).deadLetterExchange(EXCHANGE+".dlx").deadLetterRoutingKey("failed").build();
        var dlx=new DirectExchange(EXCHANGE+".dlx");
        var dlq=QueueBuilder.durable(QUEUE+".dlq").build();
        return new Declarables(ex,q,BindingBuilder.bind(q).to(ex).with(KEY),dlx,dlq,BindingBuilder.bind(dlq).to(dlx).with("failed"));
    }
}
