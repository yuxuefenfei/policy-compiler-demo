package com.example.policy.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "policies")
public class PolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(nullable = false)
    private long draftRevision;
    private Long publishedRevision;
    @Column(nullable = false)
    private Instant updatedAt;

    protected PolicyEntity() {
    }

    public PolicyEntity(String name) {
        this.name = name;
        this.draftRevision = 0;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getDraftRevision() {
        return draftRevision;
    }

    public Long getPublishedRevision() {
        return publishedRevision;
    }
}
