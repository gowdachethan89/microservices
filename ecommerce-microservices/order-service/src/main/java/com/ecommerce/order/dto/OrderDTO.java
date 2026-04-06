package com.ecommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private Long customerId;
    private String status;
    private BigDecimal totalAmount;
    private Long paymentId;
    private String paymentStatus;
    private Long createdAt;
    private Long updatedAt;
}

