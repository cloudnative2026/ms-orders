package com.cloudnative.ms_orders.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.cloudnative.ms_orders.model.OrderStatus;
public record OrderResponseDTO(Long id, int customerId, OrderStatus status,
    List<OrderItemResponseDTO> items, BigDecimal totalAmount,
    LocalDateTime createdAt, LocalDateTime updatedAt) {}
