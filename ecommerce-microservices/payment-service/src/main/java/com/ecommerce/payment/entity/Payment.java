package com.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long orderId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String status; // PENDING, COMPLETED, FAILED, REFUNDED
    
    @Column(nullable = false)
    private String paymentMethod; // CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.
    
    private String transactionId;
    
    private String failureReason;
    
    // Optional idempotency key supplied by callers to avoid duplicate processing
    private String idempotencyKey;
    
    @Column(name = "created_at")
    private Long createdAt;
    
    @Column(name = "updated_at")
    private Long updatedAt;
    
    @PrePersist
    public void prePersist() {
        long now = System.currentTimeMillis();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = System.currentTimeMillis();
    }
    
}

