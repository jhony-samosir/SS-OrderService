package com.samstore.orderservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {
    private String eventId;
    private UUID orderPublicId;
    private String paymentReference;
    private BigDecimal amount;
    private String paymentMethod;
    private OffsetDateTime completedAt;
}
