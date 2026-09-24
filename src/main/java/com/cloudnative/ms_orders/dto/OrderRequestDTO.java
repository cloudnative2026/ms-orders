package com.cloudnative.ms_orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record OrderRequestDTO(
    @NotNull @Positive Integer customerId,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items
) {
    public record Item(@NotNull @Positive Integer productId,
                       @NotNull @Min(1) @Max(100000) Integer quantity) {}
}
