package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.PaymentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "payment-service", url = "${payment.service.url}", path = "/api/payments")
public interface PaymentClient {

    @PostMapping
    PaymentDTO processPayment(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey, @RequestBody PaymentDTO payment);

    @PostMapping("/{id}/refund")
    PaymentDTO refundPayment(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

}
