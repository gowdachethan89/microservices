package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.CustomerDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerClient {

    @GetMapping("/{id}")
    CustomerDTO getCustomer(@PathVariable("id") Long id);

}
