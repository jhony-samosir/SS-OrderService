package com.samstore.orderservice.service;

import com.samstore.orderservice.entity.OutboxMessage;
import com.samstore.orderservice.messaging.RabbitMQPublisher;
import com.samstore.orderservice.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitMQPublisher rabbitMQPublisher;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxMessageRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages to process", pendingMessages.size());

        for (OutboxMessage message : pendingMessages) {
            processMessage(message);
        }
    }

    private void processMessage(OutboxMessage message) {
        log.info("Processing outbox message ID: {}, Event Type: {}", message.getId(), message.getEventType());
        try {
            // Publish to RabbitMQ
            rabbitMQPublisher.publishEvent(message.getEventType(), message.getPayload());

            // Update status to PUBLISHED
            updateStatus(message, "PUBLISHED", null);
            log.info("Outbox message ID {} published successfully", message.getId());

        } catch (Exception e) {
            log.error("Failed to publish outbox message ID: {}", message.getId(), e);
            
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown publish error";
            if (errorMsg.length() > 1000) {
                errorMsg = errorMsg.substring(0, 1000);
            }

            // Retry count logic
            int retries = message.getRetryCount() + 1;
            String status = retries >= 5 ? "FAILED" : "PENDING";

            updateStatus(message, status, errorMsg);
        }
    }

    @Transactional
    public void updateStatus(OutboxMessage message, String status, String errorMessage) {
        OutboxMessage entity = outboxMessageRepository.findById(message.getId()).orElse(null);
        if (entity != null) {
            entity.setStatus(status);
            entity.setRetryCount(entity.getRetryCount() + 1);
            entity.setErrorMessage(errorMessage);
            if ("PUBLISHED".equals(status)) {
                entity.setPublishedAt(OffsetDateTime.now());
            }
            outboxMessageRepository.save(entity);
        }
    }
}
