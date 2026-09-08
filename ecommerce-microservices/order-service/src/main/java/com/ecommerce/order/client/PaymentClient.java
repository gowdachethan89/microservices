package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.PaymentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", path = "/api/payments")
public interface PaymentClient {

    @PostMapping
    PaymentDTO processPayment(@RequestBody PaymentDTO payment);

}
