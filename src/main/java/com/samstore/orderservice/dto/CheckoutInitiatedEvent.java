package com.samstore.orderservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutInitiatedEvent {
    private String correlationId; // This acts as the order's public ID
    private Integer userId;
    private UUID cartPublicId;
    private List<CheckoutItemDto> items;
    private BigDecimal totalAmount;
    private String currencyCode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CheckoutItemDto {
        private UUID productPublicId;
        private Integer variantId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
