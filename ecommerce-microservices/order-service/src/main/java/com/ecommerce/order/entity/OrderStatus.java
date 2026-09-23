package com.ecommerce.order.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    CREATED,
    PENDING,
    CONFIRMED,
    PROCESSING,
    READY_TO_SHIP,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();
    private static final Set<OrderStatus> TERMINAL_STATES = Set.of(CANCELLED, REFUNDED);

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(CREATED, Set.of(PENDING, CONFIRMED, CANCELLED));
        transitions.put(PENDING, Set.of(CONFIRMED, CANCELLED));
        transitions.put(CONFIRMED, Set.of(PROCESSING, CANCELLED, REFUNDED));
        transitions.put(PROCESSING, Set.of(READY_TO_SHIP, CANCELLED, REFUNDED));
        transitions.put(READY_TO_SHIP, Set.of(SHIPPED, CANCELLED, REFUNDED));
        transitions.put(SHIPPED, Set.of(OUT_FOR_DELIVERY, RETURN_REQUESTED, REFUNDED));
        transitions.put(OUT_FOR_DELIVERY, Set.of(DELIVERED, RETURN_REQUESTED, REFUNDED));
        transitions.put(DELIVERED, Set.of(RETURN_REQUESTED, REFUNDED));
        transitions.put(RETURN_REQUESTED, Set.of(RETURNED, CANCELLED));
        transitions.put(RETURNED, Set.of(REFUNDED));
        transitions.put(CANCELLED, Collections.emptySet());
        transitions.put(REFUNDED, Collections.emptySet());
        return Collections.unmodifiableMap(transitions);
    }

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean canTransitionTo(OrderStatus targetStatus) {
        if (this == null || targetStatus == null || this == targetStatus) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        Set<OrderStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(targetStatus);
    }

    public static OrderStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return OrderStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
