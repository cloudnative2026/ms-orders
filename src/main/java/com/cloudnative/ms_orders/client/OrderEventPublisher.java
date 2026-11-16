package com.cloudnative.ms_orders.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.cloudnative.ms_orders.config.KafkaTopicsConfig;
import com.cloudnative.ms_orders.service.OrderChangedEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    // orders.events: canonical business event with the order snapshot (ms-report).
    record OrderEvent(String eventId, String type, Instant occurredAt, long orderId, int customerId,
            String status, BigDecimal totalAmount, List<Item> items, Instant createdAt) {}
    record Item(int productId, int quantity, BigDecimal unitPrice) {}

    // audit.timeline: who / what / when / from where (ms-audit).
    record AuditEntry(String eventId, String eventType, String entityType, String entityId,
            String actorSubject, String actorName, List<String> actorRoles, Instant occurredAt,
            String sourceIp, String userAgent, String sourceService, String detail) {}

    public OrderEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    // Runs on the request thread after commit, so the security and request context are still bound.
    @TransactionalEventListener
    public void publish(OrderChangedEvent event) {
        var order = event.order();
        var eventId = UUID.randomUUID().toString();
        var now = Instant.now();
        var items = order.items().stream().map(i -> new Item(i.productId(), i.quantity(), i.unitPrice())).toList();
        send(KafkaTopicsConfig.ORDERS_EVENTS, String.valueOf(order.id()), new OrderEvent(eventId, event.type(), now,
                order.id(), order.customerId(), order.status().name(), order.totalAmount(), items,
                order.createdAt().atZone(ZoneId.systemDefault()).toInstant()));

        String subject = null, name = null;
        List<String> roles = List.of();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            subject = auth.getToken().getSubject();
            name = auth.getToken().getClaimAsString("name");
            if (name == null) name = auth.getToken().getClaimAsString("preferred_username");
            roles = auth.getAuthorities().stream().map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).toList();
        }
        String ip = null, userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            // First hop added by the gateway/nginx; informational only, clients can forge it.
            var forwarded = request.getHeader("X-Forwarded-For");
            ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            userAgent = request.getHeader("User-Agent");
        }
        send(KafkaTopicsConfig.AUDIT_TIMELINE, eventId, new AuditEntry(eventId, event.type(), "ORDER",
                String.valueOf(order.id()), subject, name, roles, now, ip, userAgent, "ms-orders", event.detail()));
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, json.writeValueAsString(payload)).whenComplete((result, error) -> {
                if (error != null) log.warn("No se pudo publicar en {} (clave {})", topic, key, error);
            });
        } catch (KafkaException exception) {
            // The order is already committed; a broker outage must not turn it into an error.
            log.warn("No se pudo publicar en {} (clave {})", topic, key, exception);
        }
    }
}
