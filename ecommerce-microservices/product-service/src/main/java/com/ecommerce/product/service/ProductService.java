package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDTO;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public ProductDTO createProduct(ProductDTO dto) {
        log.info("Creating product: {}", dto.getName());
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCategory(dto.getCategory());
        product.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 0);
        product.setReserved(dto.getReserved() != null ? dto.getReserved() : 0);
        product.setAvailable(product.getQuantity() - product.getReserved());
        
        Product savedProduct = productRepository.save(product);
        log.info("Product created with ID: {}", savedProduct.getId());
        return mapToDTO(savedProduct);
    }

    // Reserve inventory atomically: returns true if reserved, false if insufficient
    @Transactional
    public boolean reserveInventory(Long productId, Integer quantity, String orderId, String reason) {
        log.info("Reserving {} units for product: {} (order={} reason={})", quantity, productId, orderId, reason);
        int updated = productRepository.reserveInventoryIfAvailable(productId, quantity);
        return updated > 0;
    }

    // Release reserved inventory (compensation)
    @Transactional
    public boolean releaseInventory(Long productId, Integer quantity, String orderId, String reason) {
        log.info("Releasing {} reserved units for product: {} (order={} reason={})", quantity, productId, orderId, reason);
        int updated = productRepository.releaseReservedInventory(productId, quantity);
        return updated > 0;
    }
    
    public ProductDTO getProductById(Long id) {
        log.info("Fetching product with ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        return mapToDTO(product);
    }
    
    public Map<String, Object> getInventory(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        Map<String, Object> inv = new HashMap<>();
        inv.put("productId", product.getId());
        inv.put("quantity", product.getQuantity());
        inv.put("reserved", product.getReserved());
        inv.put("available", product.getAvailable());
        inv.put("status", product.getAvailable() > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        return inv;
    }
    
    public List<ProductDTO> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public List<ProductDTO> searchProducts(String query) {
        log.info("Searching products with query: {}", query);
        return productRepository.findByNameContainingIgnoreCase(query)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getProductsByCategory(String category) {
        log.info("Fetching products in category: {}", category);
        return productRepository.findByCategoryIgnoreCase(category)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    
    public ProductDTO updateProduct(Long id, ProductDTO dto) {
        log.info("Updating product with ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCategory(dto.getCategory());
        product.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : product.getQuantity());
        product.setReserved(dto.getReserved() != null ? dto.getReserved() : product.getReserved());
        product.setAvailable(product.getQuantity() - product.getReserved());
        
        Product updatedProduct = productRepository.save(product);
        log.info("Product updated with ID: {}", updatedProduct.getId());
        return mapToDTO(updatedProduct);
    }
    
    public void deleteProduct(Long id) {
        log.info("Deleting product with ID: {}", id);
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with ID: " + id);
        }
        productRepository.deleteById(id);
        log.info("Product deleted with ID: {}", id);
    }
    
    private ProductDTO mapToDTO(Product product) {
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getQuantity(),
                product.getReserved(),
                product.getAvailable(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
    
}

