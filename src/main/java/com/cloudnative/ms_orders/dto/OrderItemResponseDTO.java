package com.cloudnative.ms_orders.dto;
import java.math.BigDecimal;
public record OrderItemResponseDTO(int productId, int quantity, BigDecimal unitPrice, ProductDTO product) {}
