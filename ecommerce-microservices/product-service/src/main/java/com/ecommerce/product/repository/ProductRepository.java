package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByCategoryIgnoreCase(String category);

    // Reserve stock atomically: increase reserved, decrease available only if enough available
    @Modifying
    @Query("UPDATE Product p SET p.reserved = p.reserved + :qty, p.available = p.available - :qty WHERE p.id = :id AND p.available >= :qty")
    int reserveInventoryIfAvailable(@Param("id") Long id, @Param("qty") Integer qty);

    // Release reserved stock (compensation) atomically
    @Modifying
    @Query("UPDATE Product p SET p.reserved = p.reserved - :qty, p.available = p.available + :qty WHERE p.id = :id AND p.reserved >= :qty")
    int releaseReservedInventory(@Param("id") Long id, @Param("qty") Integer qty);

    // Commit reserved stock to actual warehouse depletion when order is completed/delivered.
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty, p.reserved = p.reserved - :qty WHERE p.id = :id AND p.reserved >= :qty")
    int commitInventory(@Param("id") Long id, @Param("qty") Integer qty);

}
