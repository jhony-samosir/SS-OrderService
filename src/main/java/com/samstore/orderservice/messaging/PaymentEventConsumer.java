package com.samstore.orderservice.messaging;

import com.samstore.orderservice.config.RabbitMQConfig;
import com.samstore.orderservice.dto.PaymentCompletedEvent;
import com.samstore.orderservice.entity.InboxMessage;
import com.samstore.orderservice.entity.Order;
import com.samstore.orderservice.entity.OrderStatusHistory;
import com.samstore.orderservice.repository.InboxMessageRepository;
import com.samstore.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final InboxMessageRepository inboxMessageRepository;
    private final OrderRepository orderRepository;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    @Transactional
    public void consumePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent for Order Public ID: {}, Event ID: {}", event.getOrderPublicId(), event.getEventId());

        // 1. Inbox Pattern: Check for duplicate message for Idempotency
        if (inboxMessageRepository.existsById(event.getEventId())) {
            log.warn("Idempotency Triggered! Duplicate event detected. Inbox message ID {} already processed.", event.getEventId());
            return;
        }

        // 2. Business Logic: Update Order details
        Order order = orderRepository.findByPublicId(event.getOrderPublicId()).orElse(null);
        if (order == null) {
            log.error("Order not found with Public ID: {}. Skipping message processing.", event.getOrderPublicId());
            return;
        }

        String oldStatus = order.getStatus();
        
        // Update Order fields
        order.setPaymentStatus("paid");
        order.setStatus("processing");
        order.setPaymentReference(event.getPaymentReference());
        order.setUpdatedAt(OffsetDateTime.now());
        order.setUpdatedBy("PaymentConsumer");

        // Add history entry
        OrderStatusHistory history = OrderStatusHistory.builder()
                .fromStatus(oldStatus)
                .toStatus("processing")
                .notes("Payment completed. Reference: " + event.getPaymentReference())
                .createdBy("PaymentConsumer")
                .build();
        
        order.addHistory(history);
        orderRepository.save(order);

        // 3. Inbox Pattern: Save message to inbox to guarantee deduplication
        InboxMessage inboxMessage = InboxMessage.builder()
                .messageId(event.getEventId())
                .eventType("payment.completed")
                .aggregateType("order")
                .payload(event.toString())
                .processedAt(OffsetDateTime.now())
                .status("PROCESSED")
                .build();
        
        inboxMessageRepository.save(inboxMessage);

        log.info("Successfully updated order ID: {} status to 'processing' and saved Inbox ID: {}", order.getId(), event.getEventId());
    }
}
