package com.samstore.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboxMessage {

    @Id
    @Column(name = "message_id", nullable = false, length = 255)
    private String messageId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private OffsetDateTime processedAt = OffsetDateTime.now();

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "PROCESSED";
}
