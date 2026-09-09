package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service", url = "${product.service.url}", path = "/api/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);

    @PutMapping("/{id}/inventory/reserve")
    void reserveInventory(@PathVariable("id") Long id, @RequestBody java.util.Map<String, Object> body);

    @PutMapping("/{id}/inventory/release")
    void releaseInventory(@PathVariable("id") Long id, @RequestBody java.util.Map<String, Object> body);

}
