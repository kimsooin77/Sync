package com.kimsooin77.sync.api.employee;
import com.kimsooin77.sync.api.common.PageRequestFactory;
import com.kimsooin77.sync.api.common.PageResponse;
import com.kimsooin77.sync.audit.AuditLogQueryService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
@RestController
@RequestMapping("/api/employees/{employeeId}/audit-logs")
public class AuditLogController {
    private final AuditLogQueryService service;
    private final ObjectMapper mapper;
    public AuditLogController(AuditLogQueryService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @GetMapping
    public PageResponse<AuditLogResponse> list(@PathVariable Long employeeId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequestFactory.create(page, size, "createdAt", "id")
                .withSort(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(service.findForEmployee(employeeId, pageable).map(audit -> {
            try {
                JsonNode changes = mapper.readTree(audit.getChanges());
                if (changes == null || !changes.isObject()) return AuditLogResponse.from(audit, null, true);
                return AuditLogResponse.from(audit, changes, false);
            } catch (JacksonException invalidJson) {
                return AuditLogResponse.from(audit, null, true);
            }
        }));
    }
}
