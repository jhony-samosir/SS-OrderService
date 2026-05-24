package com.samstore.orderservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Integer id;
    private UUID publicId;
    private OffsetDateTime createdAt;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private String currencyCode;
    private String paymentMethod;
    private String paymentStatus;
    private String paymentReference;
    private String shippingCourier;
    private String shippingService;
    private String shippingTrackingNumber;
    private String notes;
    private OffsetDateTime estimatedDelivery;
    private List<OrderItemDto> items;
    private OrderAddressDto shippingAddress;
}
