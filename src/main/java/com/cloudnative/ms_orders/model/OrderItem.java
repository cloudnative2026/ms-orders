package com.cloudnative.ms_orders.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class OrderItem {
    private int productId;
    private int quantity;
    private int unitPrice;
}
