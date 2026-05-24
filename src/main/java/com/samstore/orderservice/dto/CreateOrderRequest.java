package com.samstore.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemDto> items;

    @NotNull(message = "Shipping address is required")
    @Valid
    private OrderAddressDto shippingAddress;

    private String notes;

    @NotNull(message = "Payment method is required")
    private String paymentMethod;

    @NotNull(message = "Shipping courier is required")
    private String shippingCourier;

    @NotNull(message = "Shipping service is required")
    private String shippingService;
}
