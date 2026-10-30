package com.cloudnative.ms_orders.service;

import com.cloudnative.ms_orders.config.ReceiptRabbitConfig;
import com.cloudnative.ms_orders.repository.ReceiptRepository;
import org.slf4j.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.receipts.worker-enabled", havingValue = "true", matchIfMissing = true)
public class ReceiptWorker {
    private static final Logger log = LoggerFactory.getLogger(ReceiptWorker.class);
    private final ReceiptRepository receipts;
    private final RabbitTemplate rabbit;
    private final ReceiptService service;
    public ReceiptWorker(ReceiptRepository receipts, RabbitTemplate rabbit, ReceiptService service) {
        this.receipts = receipts; this.rabbit = rabbit; this.service = service;
    }
    // The database row is a durable outbox. Pending jobs are retried after outages;
    // duplicate deliveries are harmless because generation locks the row and checks READY.
    @Scheduled(fixedDelayString = "${app.receipts.dispatch-delay:5000}")
    public void dispatch() {
        for (var receipt : receipts.findTop100ByGeneratedAtIsNullOrderByRequestedAtAsc()) {
            try {
                rabbit.convertAndSend(ReceiptRabbitConfig.EXCHANGE, ReceiptRabbitConfig.KEY, receipt.getOrderId());
            } catch (AmqpException error) {
                log.warn("RabbitMQ no disponible; las boletas pendientes se reintentaran", error);
                break;
            }
        }
    }
    @RabbitListener(queues = ReceiptRabbitConfig.QUEUE)
    public void receive(Long orderId) {
        service.generate(orderId);
        log.info("Boleta del pedido {} procesada", orderId);
    }
}
