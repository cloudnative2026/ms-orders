package com.cloudnative.ms_orders.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_receipt")
@Getter @Setter
public class Receipt {
    @Id private Long orderId;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String snapshot;
    @Column(nullable = false) private LocalDateTime requestedAt;
    private LocalDateTime generatedAt;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) private String html;
}
