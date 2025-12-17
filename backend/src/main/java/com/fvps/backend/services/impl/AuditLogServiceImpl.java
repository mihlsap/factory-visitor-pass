package com.fvps.backend.services.impl;

import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.repositories.AuditLogRepository;
import com.fvps.backend.services.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b>
     * <ul>
     * <li>Uses {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}. This ensures the log entry
     * is committed in a separate transaction. If the main business operation fails and rolls back,
     * the audit log entry <b>persists</b>, allowing diagnosis of the failure.</li>
     * <li>Automatically extracts the client's IP address from the HTTP request headers (handling proxies/load balancers).</li>
     * <li>Automatically resolves the current authenticated actor from the Security Context.</li>
     * </ul>
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(UUID userId, String action, String details) {
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .actor(getCurrentActorEmail())
                .action(action)
                .details(details)
                .timestamp(LocalDateTime.now(clock))
                .ipAddress(getClientIp())
                .build();

        auditLogRepository.save(log);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Implementation Note:</b> Delegates to {@link #logEvent(UUID, String, String)} with a {@code null} userId.
     * Inherits the {@code REQUIRES_NEW} transactional behaviour.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String details) {
        logEvent(null, action, details);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    private String getClientIp() {
        try {
            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve client IP. Reason: {}", e.getMessage());
        }
        return "SYSTEM/UNKNOWN";
    }

    private String getCurrentActorEmail() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.debug("Could not retrieve current actor email. Reason: {}", e.getMessage());
        }
        return "SYSTEM";
    }
}