package com.samstore.orderservice.repository;

import com.samstore.orderservice.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Integer> {
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(String status);
}
