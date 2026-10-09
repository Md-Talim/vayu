package me.talim.vayu.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Subscription() {}

    public Subscription(Endpoint endpoint, String eventType) {
        this.endpoint = endpoint;
        this.eventType = eventType;
    }

    public UUID getId() {
        return id;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public String getEventType() {
        return eventType;
    }
}
