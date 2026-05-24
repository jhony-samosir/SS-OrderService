package com.samstore.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samstore.orderservice.dto.*;
import com.samstore.orderservice.entity.*;
import com.samstore.orderservice.repository.OrderRepository;
import com.samstore.orderservice.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating new order for courier: {}, service: {}", request.getShippingCourier(), request.getShippingService());

        // Extract user public ID and username from JWT
        UUID userPublicId = extractUserPublicId();
        String username = extractUsername();
        Integer userId = extractUserId(); // fallback/mock if not standard

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemDto itemDto : request.getItems()) {
            BigDecimal itemTotal = itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            subtotal = subtotal.add(itemTotal);
        }

        BigDecimal shippingAmount = request.getShippingAddress().getCity().toLowerCase().contains("jakarta") 
                ? BigDecimal.valueOf(10000) 
                : BigDecimal.valueOf(25000); // Mock shipping cost
        BigDecimal discountAmount = BigDecimal.ZERO; // Optional discounts
        BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.11)); // 11% VAT
        BigDecimal totalAmount = subtotal.add(shippingAmount).add(taxAmount).subtract(discountAmount);

        // Build Order
        Order order = Order.builder()
                .userId(userId)
                .userPublicId(userPublicId)
                .status("pending")
                .subtotal(subtotal)
                .shippingAmount(shippingAmount)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus("unpaid")
                .shippingCourier(request.getShippingCourier())
                .shippingService(request.getShippingService())
                .notes(request.getNotes())
                .createdBy(username)
                .build();

        // Add items
        for (OrderItemDto itemDto : request.getItems()) {
            BigDecimal itemSubtotal = itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            OrderItem item = OrderItem.builder()
                    .productId(itemDto.getProductId())
                    .productPublicId(itemDto.getProductPublicId())
                    .productName(itemDto.getProductName())
                    .variantId(itemDto.getVariantId())
                    .variantPublicId(itemDto.getVariantPublicId())
                    .variantName(itemDto.getVariantName())
                    .sku(itemDto.getSku())
                    .imageUrl(itemDto.getImageUrl())
                    .unitPrice(itemDto.getUnitPrice())
                    .quantity(itemDto.getQuantity())
                    .subtotal(itemSubtotal)
                    .discountAmount(BigDecimal.ZERO)
                    .totalPrice(itemSubtotal)
                    .sellerId(itemDto.getSellerId())
                    .sellerName(itemDto.getSellerName())
                    .createdBy(username)
                    .build();
            order.addItem(item);
        }

        // Add address
        OrderAddressDto addressDto = request.getShippingAddress();
        OrderAddress address = OrderAddress.builder()
                .type("shipping")
                .recipientName(addressDto.getRecipientName())
                .phoneNumber(addressDto.getPhoneNumber())
                .streetAddress(addressDto.getStreetAddress())
                .city(addressDto.getCity())
                .state(addressDto.getState())
                .postalCode(addressDto.getPostalCode())
                .countryCode(addressDto.getCountryCode())
                .createdBy(username)
                .build();
        order.addAddress(address);

        // Add history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .fromStatus(null)
                .toStatus("pending")
                .notes("Order initialized via checkout.")
                .createdBy(username)
                .build();
        order.addHistory(history);

        // Save to DB (cascade handles items, addresses, histories)
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved successfully with ID: {}, Public ID: {}", savedOrder.getId(), savedOrder.getPublicId());

        // Create Outbox message inside the SAME transaction
        createOutboxMessage(savedOrder);

        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByPublicId(UUID publicId) {
        Order order = orderRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with public ID: " + publicId));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForCurrentUser() {
        UUID userPublicId = extractUserPublicId();
        log.info("Fetching orders for user public ID: {}", userPublicId);
        List<Order> orders = orderRepository.findByUserPublicIdOrderByCreatedAtDesc(userPublicId);
        return orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void createOutboxMessage(Order order) {
        try {
            // Create a payload object
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
            log.info("Saved outbox event 'order.created' for order ID: {}", order.getId());

        } catch (Exception e) {
            log.error("Failed to generate outbox message for order ID: {}", order.getId(), e);
            throw new RuntimeException("Outbox message generation failed", e);
        }
    }

    private UUID extractUserPublicId() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                String publicIdStr = jwt.getClaimAsString("public_id");
                if (publicIdStr != null) {
                    return UUID.fromString(publicIdStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user public ID from JWT, using random UUID", e);
        }
        return UUID.randomUUID(); // Fallback
    }

    private String extractUsername() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to extract username from security context", e);
        }
        return "system";
    }

    private Integer extractUserId() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // If there's a numeric user_id claim in JWT
                Long id = jwt.getClaimAsMap("user") != null 
                        ? (Long) jwt.getClaimAsMap("user").get("id")
                        : jwt.getClaim("user_id");
                if (id != null) {
                    return id.intValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract user ID from JWT, defaulting to mock id 1", e);
        }
        return 1; // Default/Mock user ID
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> OrderItemDto.builder()
                        .productId(item.getProductId())
                        .productPublicId(item.getProductPublicId())
                        .productName(item.getProductName())
                        .variantId(item.getVariantId())
                        .variantPublicId(item.getVariantPublicId())
                        .variantName(item.getVariantName())
                        .sku(item.getSku())
                        .imageUrl(item.getImageUrl())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .sellerId(item.getSellerId())
                        .sellerName(item.getSellerName())
                        .build())
                .collect(Collectors.toList());

        OrderAddress shippingAddressEntity = order.getAddresses().stream()
                .filter(a -> "shipping".equals(a.getType()))
                .findFirst()
                .orElse(null);

        OrderAddressDto shippingAddressDto = null;
        if (shippingAddressEntity != null) {
            shippingAddressDto = OrderAddressDto.builder()
                    .type(shippingAddressEntity.getType())
                    .recipientName(shippingAddressEntity.getRecipientName())
                    .phoneNumber(shippingAddressEntity.getPhoneNumber())
                    .streetAddress(shippingAddressEntity.getStreetAddress())
                    .city(shippingAddressEntity.getCity())
                    .state(shippingAddressEntity.getState())
                    .postalCode(shippingAddressEntity.getPostalCode())
                    .countryCode(shippingAddressEntity.getCountryCode())
                    .build();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .publicId(order.getPublicId())
                .createdAt(order.getCreatedAt())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingAmount(order.getShippingAmount())
                .discountAmount(order.getDiscountAmount())
                .currencyCode(order.getCurrencyCode())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paymentReference(order.getPaymentReference())
                .shippingCourier(order.getShippingCourier())
                .shippingService(order.getShippingService())
                .shippingTrackingNumber(order.getShippingTrackingNumber())
                .notes(order.getNotes())
                .estimatedDelivery(order.getEstimatedDelivery())
                .items(itemDtos)
                .shippingAddress(shippingAddressDto)
                .build();
    }
}
