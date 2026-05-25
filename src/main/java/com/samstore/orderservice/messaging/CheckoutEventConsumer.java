package com.samstore.orderservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samstore.orderservice.config.RabbitMQConfig;
import com.samstore.orderservice.dto.CheckoutInitiatedEvent;
import com.samstore.orderservice.entity.*;
import com.samstore.orderservice.repository.InboxMessageRepository;
import com.samstore.orderservice.repository.OrderRepository;
import com.samstore.orderservice.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutEventConsumer {

    private final InboxMessageRepository inboxMessageRepository;
    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CHECKOUT_INITIATED_QUEUE)
    @Transactional
    public void consumeCheckoutInitiated(CheckoutInitiatedEvent event) {
        log.info("Received CheckoutInitiatedEvent for Correlation ID (Order Public ID): {}, User: {}", 
                event.getCorrelationId(), event.getUserId());

        // 1. Inbox Pattern: Check for duplicate checkout message for Idempotency
        if (inboxMessageRepository.existsById(event.getCorrelationId())) {
            log.warn("Idempotency Triggered! Duplicate checkout event detected. Inbox message ID {} already processed.", event.getCorrelationId());
            return;
        }

        // 2. Business Logic: Create the Order in database
        try {
            UUID orderPublicId = UUID.fromString(event.getCorrelationId());
            
            BigDecimal subtotal = event.getItems().stream()
                    .map(CheckoutInitiatedEvent.CheckoutItemDto::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal shippingAmount = BigDecimal.valueOf(10000); // Default shipping cost
            BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.11)); // 11% VAT
            BigDecimal totalAmount = subtotal.add(shippingAmount).add(taxAmount);

            // Create Order
            Order order = Order.builder()
                    .publicId(orderPublicId)
                    .userId(event.getUserId())
                    .userPublicId(event.getUserPublicId()) // Map public ID from checkout event
                    .status("pending")
                    .subtotal(subtotal)
                    .shippingAmount(shippingAmount)
                    .discountAmount(BigDecimal.ZERO)
                    .taxAmount(taxAmount)
                    .totalAmount(totalAmount)
                    .currencyCode(event.getCurrencyCode() != null ? event.getCurrencyCode() : "IDR")
                    .paymentMethod("COD") // Default payment method
                    .paymentStatus("unpaid")
                    .shippingCourier("JNE")
                    .shippingService("REG")
                    .notes("Checkout initiated from cart.")
                    .createdBy("CartServiceConsumer")
                    .build();

            // Map and add items
            for (var itemDto : event.getItems()) {
                OrderItem item = OrderItem.builder()
                        .productId(0) // Default SPU ID for async mapped catalog
                        .productPublicId(itemDto.getProductPublicId())
                        .productName(itemDto.getProductName())
                        .variantId(itemDto.getVariantId())
                        .variantPublicId(UUID.randomUUID())
                        .unitPrice(itemDto.getUnitPrice())
                        .quantity(itemDto.getQuantity())
                        .subtotal(itemDto.getSubtotal())
                        .discountAmount(BigDecimal.ZERO)
                        .totalPrice(itemDto.getSubtotal())
                        .createdBy("CartServiceConsumer")
                        .build();
                order.addItem(item);
            }

            // Create default shipping address snapshot
            OrderAddress address = OrderAddress.builder()
                    .type("shipping")
                    .recipientName("User " + event.getUserId())
                    .phoneNumber("08123456789")
                    .streetAddress("Fulfillment Address")
                    .city("Jakarta")
                    .state("DKI Jakarta")
                    .postalCode("10110")
                    .countryCode("ID")
                    .createdBy("CartServiceConsumer")
                    .build();
            order.addAddress(address);

            // Add initial history
            OrderStatusHistory history = OrderStatusHistory.builder()
                    .fromStatus(null)
                    .toStatus("pending")
                    .notes("Order created from checkout event.")
                    .createdBy("CartServiceConsumer")
                    .build();
            order.addHistory(history);

            // Save Order to DB
            Order savedOrder = orderRepository.save(order);
            log.info("Successfully created Order ID: {}, Public ID: {} from async checkout event", 
                    savedOrder.getId(), savedOrder.getPublicId());

            // 3. Create Outbox message inside the same database transaction
            createOutboxMessage(savedOrder);

            // 4. Inbox Pattern: Save message to inbox to guarantee idempotency
            InboxMessage inboxMessage = InboxMessage.builder()
                    .messageId(event.getCorrelationId())
                    .eventType("order.checkout.initiated")
                    .aggregateType("order")
                    .payload(objectMapper.writeValueAsString(event))
                    .processedAt(OffsetDateTime.now())
                    .status("PROCESSED")
                    .build();
            
            inboxMessageRepository.save(inboxMessage);
            log.info("Saved Inbox record for processed checkout ID: {}", event.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to process async checkout for correlation ID: {}", event.getCorrelationId(), e);
            throw new RuntimeException("Async checkout processing failed", e);
        }
    }

    private void createOutboxMessage(Order order) {
        try {
            var eventPayload = new Object() {
                public final UUID orderPublicId = order.getPublicId();
                public final Integer userId = order.getUserId();
                public final UUID userPublicId = order.getUserPublicId();
                public final BigDecimal totalAmount = order.getTotalAmount();
                public final String currencyCode = order.getCurrencyCode();
                public final OffsetDateTime createdAt = order.getCreatedAt();
            };

            String payloadJson = objectMapper.writeValueAsString(eventPayload);

            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .eventType("order.created")
                    .aggregateType("order")
                    .aggregateId(order.getId())
                    .payload(payloadJson)
                    .status("PENDING")
                    .build();

            outboxMessageRepository.save(outboxMessage);
            log.info("Saved outbox event 'order.created' inside checkout transaction for order ID: {}", order.getId());

        } catch (Exception e) {
            log.error("Failed to generate outbox message during checkout event", e);
            throw new RuntimeException("Checkout outbox failed", e);
        }
    }
}
