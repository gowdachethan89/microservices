package com.ecommerce.product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;

    private String category;

    // total quantity in stock
    @Column(nullable = false)
    private Integer quantity = 0;

    // units currently reserved for orders
    @Column(nullable = false)
    private Integer reserved = 0;

    // available = quantity - reserved
    @Column(nullable = false)
    private Integer available = 0;

    @Column(name = "created_at")
    private Long createdAt;
    
    @Column(name = "updated_at")
    private Long updatedAt;
    
    @PrePersist
    public void prePersist() {
        if (this.quantity == null) this.quantity = 0;
        if (this.reserved == null) this.reserved = 0;
        this.available = this.quantity - this.reserved;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    @PreUpdate
    public void preUpdate() {
        if (this.quantity == null) this.quantity = 0;
        if (this.reserved == null) this.reserved = 0;
        this.available = this.quantity - this.reserved;
        this.updatedAt = System.currentTimeMillis();
    }
    
}
