package com.tim8.oblak.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByActorUsernameOrderByTimestampDesc(String username);
    List<AuditEvent> findByActionOrderByTimestampDesc(AuditAction action);
    List<AuditEvent> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to);
}