package com.samstore.orderservice.repository;

import com.samstore.orderservice.entity.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, String> {
}
