package com.ecommerce.order.service;

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
    private final com.ecommerce.order.client.CustomerClient customerClient;
    private final com.ecommerce.order.client.ProductClient productClient;
    private final com.ecommerce.order.client.PaymentClient paymentClient;

    public OrderDTO createOrder(OrderDTO dto) {
        log.info("Creating order for customer: {} and product: {}", dto.getCustomerId(), dto.getProductId());

        // 1. Validate customer
        try {
            com.ecommerce.order.client.dto.CustomerDTO customer = customerClient.getCustomer(dto.getCustomerId());
            if (customer == null || customer.getId() == null) {
                throw new RuntimeException("Customer validation failed for id: " + dto.getCustomerId());
            }
        } catch (Exception ex) {
            log.error("Customer validation failed: {}", ex.getMessage());
            throw new RuntimeException("Customer validation failed: " + ex.getMessage());
        }

        // 2. Validate product and stock
        com.ecommerce.order.client.dto.ProductDTO product;
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

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with ID: {}", savedOrder.getId());

        // 4. Process payment
        com.ecommerce.order.client.dto.PaymentDTO paymentRequest = new com.ecommerce.order.client.dto.PaymentDTO();
        paymentRequest.setOrderId(savedOrder.getId());
        paymentRequest.setAmount(savedOrder.getTotalAmount());
        paymentRequest.setPaymentMethod("CARD"); // default - in real system comes from client

        com.ecommerce.order.client.dto.PaymentDTO paymentResponse;
        try {
            paymentResponse = paymentClient.processPayment(paymentRequest);
        } catch (Exception ex) {
            log.error("Payment service call failed: {}", ex.getMessage());
            // mark order payment as FAILED
            savedOrder.setPaymentStatus("FAILED");
            orderRepository.save(savedOrder);
            throw new RuntimeException("Payment processing failed: " + ex.getMessage());
        }

        // 5. Update order with payment info
        savedOrder.setPaymentId(paymentResponse.getId());
        savedOrder.setPaymentStatus(paymentResponse.getStatus());
        if ("COMPLETED".equals(paymentResponse.getStatus())) {
            savedOrder.setStatus("CONFIRMED");
        } else {
            savedOrder.setStatus("CANCELLED");
        }

        Order updatedOrder = orderRepository.save(savedOrder);
        log.info("Order {} updated with payment status: {}", updatedOrder.getId(), updatedOrder.getPaymentStatus());

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
                order.getPaymentId(),
                order.getPaymentStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
    
}

