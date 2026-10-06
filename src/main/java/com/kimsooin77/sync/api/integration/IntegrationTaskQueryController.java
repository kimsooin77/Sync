package com.kimsooin77.sync.api.integration;
import com.kimsooin77.sync.api.common.PageRequestFactory;
import com.kimsooin77.sync.api.common.PageResponse;
import com.kimsooin77.sync.integration.IntegrationAction;
import com.kimsooin77.sync.integration.IntegrationTaskDetailRow;
import com.kimsooin77.sync.integration.IntegrationTaskNotFoundException;
import com.kimsooin77.sync.integration.IntegrationTaskQueryService;
import com.kimsooin77.sync.integration.IntegrationTaskStatus;
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
@RequestMapping("/api/integration-tasks")
public class IntegrationTaskQueryController {
    private final IntegrationTaskQueryService service;
    private final ObjectMapper mapper;
    public IntegrationTaskQueryController(IntegrationTaskQueryService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @GetMapping
    public PageResponse<IntegrationTaskListResponse> list(@RequestParam(required = false) IntegrationTaskStatus status,
            @RequestParam(required = false) IntegrationAction action, @RequestParam(required = false) String employeeNo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequestFactory.create(page, size, "createdAt", "id")
                .withSort(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(service.search(status, action, employeeNo, pageable).map(IntegrationTaskListResponse::from));
    }
    @GetMapping("/{id}")
    public IntegrationTaskDetailResponse detail(@PathVariable Long id) {
        IntegrationTaskDetailRow row = service.detail(id).orElseThrow(() -> new IntegrationTaskNotFoundException(id));
        try {
            JsonNode payload = mapper.readTree(row.payload());
            if (payload == null || !payload.isObject()) return IntegrationTaskDetailResponse.from(row, null, true);
            return IntegrationTaskDetailResponse.from(row, payload, false);
        } catch (JacksonException invalidJson) {
            return IntegrationTaskDetailResponse.from(row, null, true);
        }
    }
    @GetMapping("/{id}/attempts")
    public PageResponse<IntegrationAttemptResponse> attempts(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.attempts(id, PageRequestFactory.create(page, size, "attemptNo"))
                .map(IntegrationAttemptResponse::from));
    }
}
