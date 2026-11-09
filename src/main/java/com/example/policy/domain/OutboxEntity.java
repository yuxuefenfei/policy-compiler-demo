package com.example.policy.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "policy_outbox", uniqueConstraints =
@UniqueConstraint(name = "uq_outbox_event_key", columnNames = "event_key"))
public class OutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;
    @Column(nullable = false, length = 40)
    private String eventType;
    @Lob
    @Column(nullable = false)
    private String payload;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;

    protected OutboxEntity() {
    }

    public OutboxEntity(String eventKey, String payload) {
        this.eventKey = eventKey;
        this.eventType = "POLICY_PUBLISHED";
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }
}
