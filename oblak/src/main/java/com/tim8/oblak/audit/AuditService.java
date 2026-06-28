package com.tim8.oblak.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Runs in its own transaction so that audit records are persisted
     * even if the caller's transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            // Never let audit failure crash the main flow —
            // but do make sure it's visible in the logs.
            log.error("AUDIT WRITE FAILED: action={}, actor={}, resource={}, outcome={}",
                    event.getAction(), event.getActorUsername(),
                    event.getResourceId(), event.getOutcome(), e);
        }
    }
}