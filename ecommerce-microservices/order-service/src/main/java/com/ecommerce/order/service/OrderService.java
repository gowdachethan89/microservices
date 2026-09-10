package com.ecommerce.order.service;

import com.ecommerce.order.client.CustomerClient;
import com.ecommerce.order.client.PaymentClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.client.dto.CustomerDTO;
import com.ecommerce.order.client.dto.PaymentDTO;
import com.ecommerce.order.client.dto.ProductDTO;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
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

    public OrderDTO createOrder(OrderRequest request) {
        log.info("Creating order for customer: {} items count: {}", request.getCustomerId(), request.getItems() != null ? request.getItems().size() : 0);

        if (request.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOMER_ID_REQUIRED");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_ITEMS_IN_ORDER");
        }

        // Step 1: Validate customer
        try {
            CustomerDTO customer = customerClient.getCustomer(request.getCustomerId());
            if (customer == null || customer.getId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOMER_NOT_FOUND");
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Customer validation error: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CUSTOMER_NOT_FOUND");
        }

        // Step 2: Reserve inventory for each item
        List<OrderItemRequest> items = request.getItems();
        List<OrderItemRequest> reservedItems = new ArrayList<>();
        try {
            for (OrderItemRequest it : items) {
                Map<String, Object> body = Map.of(
                        "quantity", it.getQuantity(),
                        "orderId", "tmp",
                        "reason", "ORDER_RESERVATION"
                );
                try {
                    productClient.reserveInventory(it.getProductId(), body);
                    reservedItems.add(it);
                } catch (Exception rex) {
                    log.error("Failed to reserve product {} qty {}: {}", it.getProductId(), it.getQuantity(), rex.getMessage());
                    // compensate previously reserved
                    for (OrderItemRequest r : reservedItems) {
                        try {
                            productClient.releaseInventory(r.getProductId(), Map.of("quantity", r.getQuantity(), "orderId", "tmp", "reason", "RESERVATION_ROLLBACK"));
                        } catch (Exception relEx) {
                            log.error("Failed to release during compensation for product {}: {}", r.getProductId(), relEx.getMessage());
                        }
                    }
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY");
                }
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        }

        // Step 3: Fetch prices and compute totals
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest it : items) {
            ProductDTO product = productClient.getProduct(it.getProductId());
            BigDecimal price = product.getPrice();
            BigDecimal line = price.multiply(BigDecimal.valueOf(it.getQuantity()));
            subtotal = subtotal.add(line);
            OrderItem oi = new OrderItem();
            oi.setProductId(it.getProductId());
            oi.setProductName(product.getName());
            oi.setQuantity(it.getQuantity());
            oi.setUnitPrice(price);
            oi.setSubtotal(line);
            orderItems.add(oi);
        }
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.1)); // 10% tax
        BigDecimal total = subtotal.add(tax);

        // Step 4: Persist PENDING order first
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus("PENDING");
        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotalAmount(total);
        order.setPaymentStatus("PENDING");
        for (OrderItem oi : orderItems) order.addItem(oi);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created (PENDING) id={}", savedOrder.getId());

        // Step 5: Process payment
        PaymentDTO paymentReq = new PaymentDTO();
        paymentReq.setOrderId(savedOrder.getId());
        paymentReq.setAmount(total);
        paymentReq.setStatus("COMPLETED");
        paymentReq.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "CARD");

        PaymentDTO paymentResp;
        try {
            String idempotencyKey = "order-" + savedOrder.getId();
            paymentResp = paymentClient.processPayment(idempotencyKey, paymentReq);
            if (paymentResp == null || !"COMPLETED".equals(paymentResp.getStatus())) {
                // payment failed
                // release reserved inventory
                for (OrderItemRequest r : reservedItems) {
                    try {
                        productClient.releaseInventory(r.getProductId(), Map.of("quantity", r.getQuantity(), "orderId", savedOrder.getId().toString(), "reason", "PAYMENT_FAILED"));
                    } catch (Exception relEx) {
                        log.error("Failed to release after payment failure for product {}: {}", r.getProductId(), relEx.getMessage());
                    }
                }
                savedOrder.setStatus("FAILED");
                savedOrder.setPaymentStatus(paymentResp != null ? paymentResp.getStatus() : "FAILED");
                savedOrder.setPaymentId(paymentResp != null ? paymentResp.getId() : null);
                orderRepository.save(savedOrder);
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Payment processing error: {}", ex.getMessage());
            for (OrderItemRequest r : reservedItems) {
                try {
                    productClient.releaseInventory(r.getProductId(), Map.of("quantity", r.getQuantity(), "orderId", savedOrder.getId().toString(), "reason", "PAYMENT_EXCEPTION"));
                } catch (Exception relEx) {
                    log.error("Failed to release after payment exception for product {}: {}", r.getProductId(), relEx.getMessage());
                }
            }
            savedOrder.setStatus("FAILED");
            savedOrder.setPaymentStatus("FAILED");
            savedOrder.setPaymentId(null);
            orderRepository.save(savedOrder);
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED");
        }

        // Step 6: finalize order
        savedOrder.setStatus("CONFIRMED");
        savedOrder.setPaymentStatus("COMPLETED");
        savedOrder.setPaymentId(paymentResp.getId());
        orderRepository.save(savedOrder);

        return mapToDTO(savedOrder);
    }

    public OrderDTO getOrderById(Long id) {
        log.info("Fetching order with ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated", updatedOrder.getId());
        return mapToDTO(updatedOrder);
    }

    public OrderDTO updatePaymentInfo(Long id, Long paymentId, String paymentStatus) {
        log.info("Updating payment info for order: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Payment info updated for order: {}", updatedOrder.getId());
        return mapToDTO(updatedOrder);
    }

    public void deleteOrder(Long id) {
        log.info("Deleting order with ID: {}", id);
        if (!orderRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        orderRepository.deleteById(id);
        log.info("Order deleted with ID: {}", id);
    }

    private OrderDTO mapToDTO(Order order) {
        var items = order.getItems().stream().map(it -> new OrderItemDTO(it.getProductId(), it.getProductName(), it.getQuantity(), it.getUnitPrice(), it.getSubtotal())).collect(Collectors.toList());
        return new OrderDTO(
                order.getId(),
                order.getCustomerId(),
                items,
                order.getStatus(),
                order.getSubtotal(),
                order.getTax(),
                order.getTotalAmount(),
                order.getPaymentId(),
                order.getPaymentStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
    
}

