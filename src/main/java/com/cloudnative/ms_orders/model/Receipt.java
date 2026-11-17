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
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(nullable = false, columnDefinition = "TEXT") private String snapshot;
    @Column(nullable = false) private LocalDateTime requestedAt;
    private LocalDateTime generatedAt;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(columnDefinition = "TEXT") private String html;
}
