package com.ecommerce.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void shouldAllowValidSequentialTransitions() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PENDING)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.READY_TO_SHIP)).isTrue();
        assertThat(OrderStatus.READY_TO_SHIP.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.RETURN_REQUESTED)).isTrue();
        assertThat(OrderStatus.RETURN_REQUESTED.canTransitionTo(OrderStatus.RETURNED)).isTrue();
        assertThat(OrderStatus.RETURNED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.RETURNED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void shouldRejectTransitionsFromTerminalStates() {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CREATED)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
    }

    @Test
    void shouldRejectSameStateAndNullTargets() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatus.CREATED.canTransitionTo(null)).isFalse();
        assertThat(OrderStatus.fromValue("ready-to-ship")).isEqualTo(OrderStatus.READY_TO_SHIP);
        assertThat(OrderStatus.fromValue("unknown")).isNull();
    }
}
