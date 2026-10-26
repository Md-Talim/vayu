package me.talim.vayu.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 10;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lock_token")
    private UUID lockToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "delivery")
    private List<DeliveryAttempt> attempts = new ArrayList<>();

    protected Delivery() {}

    public Delivery(Event event, Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockToken() {
        return lockToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<DeliveryAttempt> getAttempts() {
        return attempts;
    }

    public void markDelivered(Instant now) {
        this.status = DeliveryStatus.DELIVERED;
        this.nextAttemptAt = null;
        this.completedAt = now;
        this.lockedAt = null;
        this.lockToken = null;
        this.updatedAt = now;
    }

    public void scheduleRetry(Instant nextAttemptAt, Instant now) {
        this.status = DeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.completedAt = null;
        this.lockedAt = null;
        this.lockToken = null;
        this.updatedAt = now;
    }

    public void markFailed(Instant now) {
        this.status = DeliveryStatus.FAILED;
        this.nextAttemptAt = null;
        this.completedAt = now;
        this.lockedAt = null;
        this.lockToken = null;
        this.updatedAt = now;
    }
}
