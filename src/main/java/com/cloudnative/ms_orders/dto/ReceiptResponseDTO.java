package com.cloudnative.ms_orders.dto;
import java.time.LocalDateTime;
public record ReceiptResponseDTO(long orderId, String status, String number,
        LocalDateTime requestedAt, LocalDateTime generatedAt, String html) {}
