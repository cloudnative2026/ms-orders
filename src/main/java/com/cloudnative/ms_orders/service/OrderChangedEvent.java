package com.cloudnative.ms_orders.service;

import com.cloudnative.ms_orders.dto.OrderResponseDTO;
import com.cloudnative.ms_orders.model.OrderStatus;

// Raised inside the order transaction; published to Kafka only after it commits.
public record OrderChangedEvent(String type, OrderResponseDTO order, String detail) {
    static String statusType(OrderStatus status) {
        return switch (status) {
            case CREADO -> "OrderCreated";
            case ACEPTADO -> "OrderAccepted";
            case EN_PREPARACION -> "OrderPreparing";
            case DESPACHADO -> "OrderDispatched";
            case ENTREGADO -> "OrderDelivered";
            case CANCELADO -> "OrderCancelled";
        };
    }
}
