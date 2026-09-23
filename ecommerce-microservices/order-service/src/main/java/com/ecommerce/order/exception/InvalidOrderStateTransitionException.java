package com.ecommerce.order.exception;

import com.ecommerce.order.entity.OrderStatus;

public class InvalidOrderStateTransitionException extends IllegalStateException {
    private final OrderStatus currentStatus;
    private final OrderStatus newStatus;

    public InvalidOrderStateTransitionException(OrderStatus currentStatus, OrderStatus newStatus) {
        super("Invalid order state transition from " + currentStatus + " to " + newStatus);
        this.currentStatus = currentStatus;
        this.newStatus = newStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }
}
