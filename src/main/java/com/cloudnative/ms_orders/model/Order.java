package com.cloudnative.ms_orders.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int customerId;
    // Exact JWT identity; numeric customerId is business metadata, never an access credential.
    @Column(length = 512)
    private String ownerSubject;
    @Column(length = 512)
    private String ownerIssuer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private OrderStatus status;
    @ElementCollection
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "id"))
    private List<OrderItem> items;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Version @Column(nullable = false)
    private Long version;
}
