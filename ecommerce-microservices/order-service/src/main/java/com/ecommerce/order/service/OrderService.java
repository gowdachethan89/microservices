package com.ecommerce.order.service;

import com.ecommerce.order.client.CustomerClient;
import com.ecommerce.order.client.PaymentClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.client.dto.CustomerDTO;
import com.ecommerce.order.client.dto.PaymentDTO;
import com.ecommerce.order.client.dto.ProductDTO;
import com.ecommerce.order.client.dto.RefundRequest;
import com.ecommerce.order.dto.*;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.InvalidOrderStateTransitionException;
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
        order.setStatus(OrderStatus.CREATED.name());
        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotalAmount(total);
        order.setPaymentStatus("PENDING");
        for (OrderItem oi : orderItems) order.addItem(oi);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created (CREATED) id={}", savedOrder.getId());
        savedOrder.setStatus(OrderStatus.PENDING.name());
        orderRepository.save(savedOrder);

        // Step 5: Process payment
        PaymentDTO paymentReq = new PaymentDTO();
        paymentReq.setOrderId(savedOrder.getId());
        paymentReq.setCustomerId(savedOrder.getCustomerId());
        paymentReq.setAmount(total);
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
        savedOrder.setStatus(OrderStatus.CONFIRMED.name());
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
        OrderStatus enumStatus = OrderStatus.fromValue(status);
        if (enumStatus == null) {
            return Collections.emptyList();
        }
        return orderRepository.findByStatus(enumStatus.name())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public OrderDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Updating order {} status to: {}", orderId, newStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        OrderStatus currentStatus = OrderStatus.fromValue(order.getStatus());
        if (currentStatus == null) {
            currentStatus = OrderStatus.CREATED;
        }
        if (newStatus == null) {
            throw new IllegalArgumentException("Order status cannot be null");
        }
        if (currentStatus == newStatus) {
            log.info("Order {} already in status {}. No-op.", orderId, newStatus);
            return mapToDTO(order);
        }
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidOrderStateTransitionException(currentStatus, newStatus);
        }

        order.setStatus(newStatus.name());

        if(newStatus.equals(OrderStatus.CANCELLED)) {
            cancelOrder(orderId, "Order cancelled by user");
            order.setCancellationReason("Order cancelled by user");
        }
        if(newStatus.equals(OrderStatus.DELIVERED)) {
            deliverOrder(orderId);
            order.setCancellationReason("Order delivered to user");
        }
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated to {}", updatedOrder.getId(), newStatus);
        return mapToDTO(updatedOrder);
    }

    public OrderDTO updateOrderStatus(Long id, String status) {
        OrderStatus newStatus = OrderStatus.fromValue(status);
        if (newStatus == null) {
            throw new IllegalArgumentException("Unsupported order status: " + status);
        }
        return updateOrderStatus(id, newStatus);
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

    public OrderDTO cancelOrder(Long orderId, String reason) {
        log.info("Cancelling order {} with reason: {}", orderId, reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        String currentStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase(Locale.ROOT);

        if ("CANCELLED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ORDER_ALREADY_CANCELLED");
        }
        if ("SHIPPED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_BE_CANCELLED");
        }
        // allow cancellation for PENDING, CONFIRMED, COMPLETED, DELIVERED (we will branch for inventory handling)
        if (!List.of("PENDING","CONFIRMED","COMPLETED","DELIVERED").contains(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATE_FOR_CANCELLATION");
        }

        if (order.getPaymentId() != null) {
            try {
                PaymentDTO refund = paymentClient.refundPayment(
                        order.getPaymentId(),
                        new RefundRequest(
                                reason != null ? reason : "Customer request",
                                order.getTotalAmount()
                        )
                );
                log.info("Refund processed for order {} paymentId {}: {}", orderId, order.getPaymentId(), refund.getStatus());
            } catch (Exception ex) {
                log.error("Refund failed for order {} paymentId {}: {}", orderId, order.getPaymentId(), ex.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "REFUND_PROCESSING_FAILED");
            }
        }

        boolean wasCommitted = "COMPLETED".equals(currentStatus) || "DELIVERED".equals(currentStatus);

        for (OrderItem item : order.getItems()) {
            Map<String, Object> body = Map.of(
                    "quantity", item.getQuantity(),
                    "orderId", orderId,
                    "reason", "ORDER_CANCELLED"
            );
            try {
                if (wasCommitted) {
                    // stock already deducted; restock the physical quantity
                    productClient.restockInventory(item.getProductId(), body);
                } else {
                    // only reserved, release it
                    productClient.releaseInventory(item.getProductId(), body);
                }
            } catch (Exception ex) {
                log.error("Failed to adjust inventory for product {} during cancellation of order {}: {}",
                        item.getProductId(), orderId, ex.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "INVENTORY_ADJUSTMENT_FAILED");
            }
        }

        order.setStatus("CANCELLED");
        order.setPaymentStatus("REFUNDED");
        order.setCancellationReason(reason != null ? reason : "Customer request");
        Order updatedOrder = orderRepository.save(order);

        log.info("Order {} cancelled successfully", orderId);
        return mapToDTO(updatedOrder);
    }

    public void deliverOrder(Long orderId) {
        log.info("Delivering order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        String currentStatus = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase(Locale.ROOT);

        // Check if order is in a valid state for delivery (CONFIRMED or SHIPPED)
        if (!"DELIVERED".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATE_FOR_DELIVERY");
        }

        // Release reserved inventory for all items in the order as they are now delivered
        for (OrderItem item : order.getItems()) {
            Map<String, Object> body = Map.of(
                    "quantity", item.getQuantity(),
                    "orderId", orderId,
                    "reason", "ORDER_DELIVERED"
            );
            try {
                // Commit the inventory as the order is being fulfilled/delivered
                productClient.commitInventory(item.getProductId(), body);
                log.info("Inventory committed for product {} quantity {} during delivery", item.getProductId(), item.getQuantity());
            } catch (Exception ex) {
                log.error("Failed to commit inventory for product {} during delivery of order {}: {}",
                        item.getProductId(), orderId, ex.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "INVENTORY_COMMIT_FAILED");
            }
        }

        log.info("Order {} delivered successfully", orderId);
    }

    private OrderDTO mapToDTO(Order order) {
        var items = order.getItems().stream()
                .map(it -> new OrderItemDTO(
                        it.getProductId(),
                        it.getProductName(),
                        it.getQuantity(),
                        it.getUnitPrice(),
                        it.getSubtotal()
                ))
                .collect(Collectors.toList());

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
                order.getCancellationReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
    
}

