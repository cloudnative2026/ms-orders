package com.cloudnative.ms_orders.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.cloudnative.ms_orders.model.OrderStatus;

public record OrderResponseDTO(
    int id,
    int customerId,
    OrderStatus status,
    List<OrderItemResponseDTO> items,
    int totalAmount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
