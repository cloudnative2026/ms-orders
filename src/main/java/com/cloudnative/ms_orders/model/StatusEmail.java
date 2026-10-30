package com.cloudnative.ms_orders.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="status_email") @Getter @Setter
public class StatusEmail {
    @Id @Column(length=36) private String id;
    @Column(nullable=false) private Long orderId;
    @Column(length=320) private String recipient;
    @Column(nullable=false, length=30) private String previousStatus;
    @Column(nullable=false, length=30) private String newStatus;
    @Column(nullable=false) private LocalDateTime occurredAt;
    @Column(nullable=false, length=30) private String state;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private LocalDateTime nextAttemptAt;
    private LocalDateTime sentAt;
}
