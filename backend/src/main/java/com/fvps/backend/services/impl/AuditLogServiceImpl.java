package com.fvps.backend.services.impl;

import com.fvps.backend.domain.dto.audit.AuditLogDto;
import com.fvps.backend.domain.entities.AuditLog;
import com.fvps.backend.repositories.AuditLogRepository;
import com.fvps.backend.services.AuditLogService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
     * <li>Uses {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}. This
     * ensures the log entry
     * is committed in a separate transaction. If the main business operation fails
     * and rolls back,
     * the audit log entry <b>persists</b>, allowing diagnosis of the failure.</li>
     * <li>Automatically extracts the client's IP address from the HTTP request
     * headers (handling proxies/load balancers).</li>
     * <li>Automatically resolves the current authenticated actor from the Security
     * Context.</li>
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
     * <b>Implementation Note:</b> Delegates to
     * {@link #logEvent(UUID, String, String)} with a {@code null} userId.
     * Inherits the {@code REQUIRES_NEW} transactional behaviour.
     * </p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String details) {
        logEvent(null, action, details);
    }

    @Override
    public Page<AuditLogDto> getLogsWithFilters(
            String actor,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String search,
            Pageable pageable) {
        Specification<AuditLog> spec = (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter: Actor (Exact or Partial)
            if (StringUtils.hasText(actor)) {
                predicates.add(cb.like(cb.lower(root.get("actor")), "%" + actor.toLowerCase() + "%"));
            }

            // Filter: Action (Exact match)
            if (StringUtils.hasText(action) && !"ALL".equalsIgnoreCase(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            // Filter: Date From
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }

            // Filter: Date To
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            // Filter: Global Search (Search bar)
            if (StringUtils.hasText(search)) {
                String likePattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("actor")), likePattern),
                        cb.like(cb.lower(root.get("action")), likePattern),
                        cb.like(cb.lower(root.get("details")), likePattern),
                        cb.like(cb.lower(root.get("ipAddress")), likePattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable)
                .map(this::toDto);
    }

    private AuditLogDto toDto(AuditLog log) {
        return AuditLogDto.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .actor(log.getActor())
                .action(log.getAction())
                .details(log.getDetails())
                .timestamp(log.getTimestamp())
                .ipAddress(log.getIpAddress())
                .build();
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