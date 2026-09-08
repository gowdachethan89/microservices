package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByCategoryIgnoreCase(String category);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStockIfAvailable(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("qty") Integer qty);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int incrementStock(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("qty") Integer qty);
}

