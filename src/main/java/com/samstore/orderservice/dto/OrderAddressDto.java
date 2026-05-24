package com.samstore.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderAddressDto {

    private String type; // shipping, billing

    @NotNull(message = "Recipient name is required")
    private String recipientName;

    @NotNull(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Street address is required")
    private String streetAddress;

    @NotNull(message = "City is required")
    private String city;

    @NotNull(message = "State is required")
    private String state;

    @NotNull(message = "Postal code is required")
    private String postalCode;

    @Builder.Default
    private String countryCode = "ID";
}
