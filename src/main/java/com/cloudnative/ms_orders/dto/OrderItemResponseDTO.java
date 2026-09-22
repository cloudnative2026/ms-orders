package com.cloudnative.ms_orders.dto;

public record OrderItemResponseDTO(
    int productId,
    int quantity,
    int unitPrice,
    ProductDTO product
) {}
