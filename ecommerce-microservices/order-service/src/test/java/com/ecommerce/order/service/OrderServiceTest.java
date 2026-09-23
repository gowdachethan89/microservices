package com.ecommerce.order.service;

import com.ecommerce.order.client.CustomerClient;
import com.ecommerce.order.client.PaymentClient;
import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.dto.OrderDTO;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderStatus;
import com.ecommerce.order.exception.InvalidOrderStateTransitionException;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void updateOrderStatus_shouldAllowValidSequentialPath() {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(99L);
        order.setItems(new ArrayList<>());
        order.setStatus(OrderStatus.CREATED.name());

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDTO pending = orderService.updateOrderStatus(1L, OrderStatus.PENDING);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING.name());

        order.setStatus(OrderStatus.PENDING.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO confirmed = orderService.updateOrderStatus(1L, OrderStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED.name());

        order.setStatus(OrderStatus.CONFIRMED.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO processing = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);
        assertThat(processing.getStatus()).isEqualTo(OrderStatus.PROCESSING.name());

        order.setStatus(OrderStatus.PROCESSING.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO readyToShip = orderService.updateOrderStatus(1L, OrderStatus.READY_TO_SHIP);
        assertThat(readyToShip.getStatus()).isEqualTo(OrderStatus.READY_TO_SHIP.name());

        order.setStatus(OrderStatus.READY_TO_SHIP.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO shipped = orderService.updateOrderStatus(1L, OrderStatus.SHIPPED);
        assertThat(shipped.getStatus()).isEqualTo(OrderStatus.SHIPPED.name());

        order.setStatus(OrderStatus.SHIPPED.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO outForDelivery = orderService.updateOrderStatus(1L, OrderStatus.OUT_FOR_DELIVERY);
        assertThat(outForDelivery.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY.name());

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        OrderDTO delivered = orderService.updateOrderStatus(1L, OrderStatus.DELIVERED);
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED.name());
    }

    @Test
    void updateOrderStatus_shouldRejectInvalidTransition() {
        Order order = new Order();
        order.setId(2L);
        order.setCustomerId(88L);
        order.setItems(new ArrayList<>());
        order.setStatus(OrderStatus.PENDING.name());

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));

        InvalidOrderStateTransitionException exception = catchThrowableOfType(
                () -> orderService.updateOrderStatus(2L, OrderStatus.REFUNDED),
                InvalidOrderStateTransitionException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getCurrentStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(exception.getNewStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void updateOrderStatus_shouldRejectTransitionsFromTerminalStates() {
        Order cancelledOrder = new Order();
        cancelledOrder.setId(3L);
        cancelledOrder.setCustomerId(55L);
        cancelledOrder.setItems(new ArrayList<>());
        cancelledOrder.setStatus(OrderStatus.CANCELLED.name());

        when(orderRepository.findById(3L)).thenReturn(Optional.of(cancelledOrder));

        InvalidOrderStateTransitionException exception = catchThrowableOfType(
                () -> orderService.updateOrderStatus(3L, OrderStatus.CREATED),
                InvalidOrderStateTransitionException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getCurrentStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(exception.getNewStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void updateOrderStatus_shouldTreatSameStateAsNoOp() {
        Order order = new Order();
        order.setId(4L);
        order.setCustomerId(60L);
        order.setItems(new ArrayList<>());
        order.setStatus(OrderStatus.DELIVERED.name());

        when(orderRepository.findById(4L)).thenReturn(Optional.of(order));

        OrderDTO result = orderService.updateOrderStatus(4L, OrderStatus.DELIVERED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED.name());
    }
}
