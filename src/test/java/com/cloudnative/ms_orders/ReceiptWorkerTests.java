package com.cloudnative.ms_orders;
import com.cloudnative.ms_orders.config.ReceiptRabbitConfig;
import com.cloudnative.ms_orders.model.Receipt;
import com.cloudnative.ms_orders.repository.ReceiptRepository;
import com.cloudnative.ms_orders.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.List;
import static org.mockito.Mockito.*;

class ReceiptWorkerTests {
    @Test void pendingJobsSurviveBrokerFailureAndAreRetried() {
        var repo = mock(ReceiptRepository.class);
        var rabbit = mock(RabbitTemplate.class);
        var service = mock(ReceiptService.class);
        var pending = new Receipt(); pending.setOrderId(42L);
        when(repo.findTop100ByGeneratedAtIsNullOrderByRequestedAtAsc()).thenReturn(List.of(pending));
        var worker = new ReceiptWorker(repo, rabbit, service);
        doThrow(new AmqpException("offline")).doNothing().when(rabbit)
            .convertAndSend(ReceiptRabbitConfig.EXCHANGE, ReceiptRabbitConfig.KEY, (Object)42L);
        worker.dispatch();
        worker.dispatch();
        verify(rabbit, times(2)).convertAndSend(ReceiptRabbitConfig.EXCHANGE, ReceiptRabbitConfig.KEY, (Object)42L);
        verify(repo, never()).delete(any());
        worker.receive(42L);
        verify(service).generate(42L);
    }
}
