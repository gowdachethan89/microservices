package com.ecommerce.order.service;

import com.ecommerce.order.client.CustomerClient;
import com.ecommerce.order.client.PaymentClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.client.dto.CustomerDTO;
import com.ecommerce.order.client.dto.PaymentDTO;
import com.ecommerce.order.client.dto.ProductDTO;
import com.ecommerce.order.dto.OrderDTO;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    public OrderDTO createOrder(OrderDTO dto) {
        log.info("Creating order for customer: {} and product: {}", dto.getCustomerId(), dto.getProductId());

        // Basic validation for incoming request to avoid calling downstream services with nulls
        if (dto.getCustomerId() == null) {
            throw new RuntimeException("Customer id is required");
        }
        if (dto.getProductId() == null) {
            throw new RuntimeException("Product id is required");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new RuntimeException("Quantity is required and must be > 0");
        }

        // 1. Validate customer
        try {
            CustomerDTO customer = customerClient.getCustomer(dto.getCustomerId());
            if (customer == null || customer.getId() == null) {
                throw new RuntimeException("Customer validation failed for id: " + dto.getCustomerId());
            }
        } catch (Exception ex) {
            log.error("Customer validation failed: {}", ex.getMessage());
            throw new RuntimeException("Customer validation failed: " + ex.getMessage());
        }

        // 2. Validate product and stock, then attempt reservation
        ProductDTO product;
        try {
            product = productClient.getProduct(dto.getProductId());
            if (product == null || product.getId() == null) {
                throw new RuntimeException("Product not found: " + dto.getProductId());
            }
            if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                throw new RuntimeException("Invalid quantity");
            }
            if (product.getStock() == null || product.getStock() < dto.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product: " + dto.getProductId());
            }
            // Try to reserve atomically. ProductService does an atomic decrement; if it fails it will return 409 which Feign will surface as exception
            try {
                productClient.reserveStock(dto.getProductId(), java.util.Collections.singletonMap("quantity", dto.getQuantity()));
            } catch (Exception rex) {
                log.error("Stock reservation failed for product {} qty {}: {}", dto.getProductId(), dto.getQuantity(), rex.getMessage());
                throw new RuntimeException("Insufficient stock or reservation failed: " + rex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Product validation failed: {}", ex.getMessage());
            throw new RuntimeException("Product validation failed: " + ex.getMessage());
        }

        // 3. Create order in local DB (PENDING)
        Order order = new Order();
        order.setCustomerId(dto.getCustomerId());
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setTotalAmount(dto.getTotalAmount());
        order.setPaymentMethod(dto.getPaymentMethod());
n        Order savedOrder = orderRepository.save(order);
        log.info("Order created with ID: {}", savedOrder.getId());
n        // 4. Process payment
        PaymentDTO paymentRequest = new PaymentDTO();
        paymentRequest.setOrderId(savedOrder.getId());
        paymentRequest.setAmount(savedOrder.getTotalAmount());
        paymentRequest.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : "CARD"); // prefer client-provided method

        PaymentDTO paymentResponse;
        try {
            // use an idempotency key derived from the order id to protect against duplicate charges
            String idempotencyKey = "order-" + savedOrder.getId();
            paymentResponse = paymentClient.processPayment(idempotencyKey, paymentRequest);
        } catch (Exception ex) {
            log.error("Payment service call failed: {}", ex.getMessage());
            // mark order payment as FAILED and release reserved stock
            savedOrder.setPaymentStatus("FAILED");
            savedOrder.setStatus("CANCELLED");
            orderRepository.save(savedOrder);
            try {
                productClient.releaseStock(dto.getProductId(), java.util.Collections.singletonMap("quantity", dto.getQuantity()));
            } catch (Exception rex) {
                log.error("Failed to release stock after payment failure for order {}: {}", savedOrder.getId(), rex.getMessage());
            }
            throw new RuntimeException("Payment processing failed: " + ex.getMessage());
        }
n        // 5. Update order with payment info and finalize; if final save fails attempt compensation (refund + release)
        savedOrder.setPaymentId(paymentResponse.getId());
        savedOrder.setPaymentStatus(paymentResponse.getStatus());
        if ("COMPLETED".equals(paymentResponse.getStatus())) {
            savedOrder.setStatus("CONFIRMED");
        } else {
            savedOrder.setStatus("CANCELLED");
        }

        Order updatedOrder;
        try {
            updatedOrder = orderRepository.save(savedOrder);
            log.info("Order {} updated with payment status: {}", updatedOrder.getId(), updatedOrder.getPaymentStatus());
        } catch (Exception ex) {
            log.error("Failed to persist order update after payment for order {}: {}", savedOrder.getId(), ex.getMessage());
            // compensate: if payment completed -> refund, and always release stock
            try {
                if (paymentResponse != null && "COMPLETED".equals(paymentResponse.getStatus())) {
                    try {
                        paymentClient.refundPayment(paymentResponse.getId(), "Order update failed");
                    } catch (Exception refundEx) {
                        log.error("Failed to refund payment {} after order persistence failure: {}", paymentResponse.getId(), refundEx.getMessage());
                    }
                }
                productClient.releaseStock(dto.getProductId(), java.util.Collections.singletonMap("quantity", dto.getQuantity()));
            } catch (Exception compEx) {
                log.error("Compensation failed for order {}: {}", savedOrder.getId(), compEx.getMessage());
            }
            throw new RuntimeException("Failed to finalize order after payment: " + ex.getMessage());
        }

        return mapToDTO(updatedOrder);
    }
    
    public OrderDTO getOrderById(Long id) {
        log.info("Fetching order with ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
        return mapToDTO(order);
    }
    
    public List<OrderDTO> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        return orderRepository.findByCustomerId(customerId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public List<OrderDTO> getOrdersByStatus(String status) {
        log.info("Fetching orders with status: {}", status);
        return orderRepository.findByStatus(status)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public OrderDTO updateOrderStatus(Long id, String status) {
        log.info("Updating order {} status to: {}", id, status);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
        
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated", updatedOrder.getId());
        return mapToDTO(updatedOrder);
    }
    
    public OrderDTO updatePaymentInfo(Long id, Long paymentId, String paymentStatus) {
        log.info("Updating payment info for order: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
        
        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Payment info updated for order: {}", updatedOrder.getId());
        return mapToDTO(updatedOrder);
    }
    
    public void deleteOrder(Long id) {
        log.info("Deleting order with ID: {}", id);
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with ID: " + id);
        }
        orderRepository.deleteById(id);
        log.info("Order deleted with ID: {}", id);
    }
    
    private OrderDTO mapToDTO(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getPaymentId(),
                order.getPaymentStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
    
}

