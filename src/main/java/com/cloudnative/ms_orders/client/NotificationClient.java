package com.cloudnative.ms_orders.client;

import java.time.LocalDateTime;
import com.cloudnative.ms_orders.dto.OrderResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private final RabbitTemplate rabbit;
    private final String exchange;
    private final String routingKey;

    // Same shape as ms-notifications' Message DTO.
    record Notification(String user, String content, String endpoint, LocalDateTime date) {}

    public NotificationClient(RabbitTemplate rabbit, @Value("${app.rabbitmq.exchange}") String exchange,
            @Value("${app.rabbitmq.routing-key}") String routingKey) {
        this.rabbit = rabbit;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void orderCreated(OrderResponseDTO order, String subject) {
        var content = "Pedido #" + order.id() + " recibido. Total: $" + order.totalAmount();
        try {
            rabbit.convertAndSend(exchange, routingKey,
                    new Notification(subject, content, "/api/v1/orders/" + order.id(), LocalDateTime.now()));
        } catch (AmqpException exception) {
            // The order is already saved; a broker outage must not turn it into an error.
            log.warn("No se pudo publicar la notificacion del pedido {}", order.id(), exception);
        }
    }
}
