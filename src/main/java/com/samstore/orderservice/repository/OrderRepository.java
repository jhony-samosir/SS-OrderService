package com.samstore.orderservice.repository;

import com.samstore.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    Optional<Order> findByPublicId(UUID publicId);
    java.util.List<Order> findByUserPublicIdOrderByCreatedAtDesc(UUID userPublicId);
}
