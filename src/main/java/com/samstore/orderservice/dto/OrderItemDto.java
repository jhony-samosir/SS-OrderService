package com.samstore.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDto {

    @NotNull(message = "Product ID is required")
    private Integer productId;

    @NotNull(message = "Product Public ID is required")
    private UUID productPublicId;

    @NotNull(message = "Product name is required")
    private String productName;

    private Integer variantId;
    private UUID variantPublicId;
    private String variantName;
    private String sku;
    private String imageUrl;

    @NotNull(message = "Unit price is required")
    @Min(value = 0, message = "Unit price must be non-negative")
    private BigDecimal unitPrice;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private Integer sellerId;
    private String sellerName;
}
