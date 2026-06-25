package com.tim8.oblak.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private AuditOutcome outcome;

    // Who performed the action (null for anonymous, e.g. failed login with unknown username)
    @Column(length = 128)
    private String actorUsername;

    // The resource being acted on (projectId, username being registered, etc.)
    @Column(length = 256)
    private String resourceId;

    // Optional human-readable detail (e.g. malicious score, failure reason)
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 64)
    private String ipAddress;

    protected AuditEvent() {}

    private AuditEvent(Builder builder) {
        this.timestamp     = Instant.now();
        this.action        = builder.action;
        this.outcome       = builder.outcome;
        this.actorUsername = builder.actorUsername;
        this.resourceId    = builder.resourceId;
        this.detail        = builder.detail;
        this.ipAddress     = builder.ipAddress;
    }

    // --- Getters ---
    public UUID getId()              { return id; }
    public Instant getTimestamp()    { return timestamp; }
    public AuditAction getAction()   { return action; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getActorUsername() { return actorUsername; }
    public String getResourceId()    { return resourceId; }
    public String getDetail()        { return detail; }
    public String getIpAddress()     { return ipAddress; }

    public static Builder builder(AuditAction action, AuditOutcome outcome) {
        return new Builder(action, outcome);
    }

    public static final class Builder {
        private final AuditAction action;
        private final AuditOutcome outcome;
        private String actorUsername;
        private String resourceId;
        private String detail;
        private String ipAddress;

        private Builder(AuditAction action, AuditOutcome outcome) {
            this.action  = action;
            this.outcome = outcome;
        }

        public Builder actor(String username)   { this.actorUsername = username; return this; }
        public Builder resource(String id)      { this.resourceId    = id;       return this; }
        public Builder resource(UUID id)        { return resource(id.toString()); }
        public Builder detail(String detail)    { this.detail        = detail;   return this; }
        public Builder ip(String ip)            { this.ipAddress     = ip;       return this; }
        public AuditEvent build()               { return new AuditEvent(this); }
    }
}