package com.kimsooin77.sync.api.employee;
import com.kimsooin77.sync.audit.AuditAction;
import com.kimsooin77.sync.audit.AuditLog;
import com.kimsooin77.sync.audit.AuditSource;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
public record AuditLogResponse(Long id, AuditAction action, AuditSource source, JsonNode changes,
                               boolean changesParseError, Instant createdAt) {
    public static AuditLogResponse from(AuditLog audit, JsonNode changes, boolean parseError) {
        return new AuditLogResponse(audit.getId(), audit.getAction(), audit.getSource(), changes, parseError, audit.getCreatedAt());
    }
}
