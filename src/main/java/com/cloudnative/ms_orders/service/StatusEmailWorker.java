package com.cloudnative.ms_orders.service;
import com.cloudnative.ms_orders.config.StatusEmailRabbitConfig;
import com.cloudnative.ms_orders.repository.StatusEmailRepository;
import java.time.LocalDateTime;
import org.slf4j.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="app.email.worker-enabled",havingValue="true",matchIfMissing=true)
public class StatusEmailWorker {
    private static final Logger log=LoggerFactory.getLogger(StatusEmailWorker.class);
    private final StatusEmailRepository emails;
    private final RabbitTemplate rabbit;
    private final StatusEmailDelivery delivery;
    public StatusEmailWorker(StatusEmailRepository emails,RabbitTemplate rabbit,StatusEmailDelivery delivery) {
        this.emails=emails;this.rabbit=rabbit;this.delivery=delivery;
    }
    @Scheduled(fixedDelayString="${app.email.dispatch-delay:5000}")
    public void dispatch() {
        for(var event:emails.findTop100ByStateAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc("PENDING",LocalDateTime.now())) {
            try { rabbit.convertAndSend(StatusEmailRabbitConfig.EXCHANGE,StatusEmailRabbitConfig.KEY,event.getId()); }
            catch(AmqpException error) { log.warn("RabbitMQ no disponible; los correos quedan pendientes");break; }
        }
    }
    @RabbitListener(queues=StatusEmailRabbitConfig.QUEUE)
    public void receive(String eventId) { delivery.deliver(eventId); }
}
