package com.cloudnative.ms_orders.dto;

import java.math.BigDecimal;

public record ProductDTO(
    Long id,
    String name,
    String description,
    BigDecimal price,
    Integer stock,
    String imageUrl,
    Boolean active
) {}
