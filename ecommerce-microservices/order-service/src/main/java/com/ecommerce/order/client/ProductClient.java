package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${product.service.url}", path = "/api/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);

    @PostMapping("/{id}/reserve")
    void reserveStock(@PathVariable("id") Long id, @RequestBody java.util.Map<String, Integer> body);

    @PostMapping("/{id}/release")
    void releaseStock(@PathVariable("id") Long id, @RequestBody java.util.Map<String, Integer> body);

}
