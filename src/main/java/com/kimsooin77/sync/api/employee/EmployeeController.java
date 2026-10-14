package com.kimsooin77.sync.api.employee;

import com.kimsooin77.sync.api.common.PageRequestFactory;
import com.kimsooin77.sync.api.common.PageResponse;
import com.kimsooin77.sync.api.common.PublicSortFactory;
import com.kimsooin77.sync.employee.EmployeeNotFoundException;
import com.kimsooin77.sync.employee.EmployeeQueryService;
import com.kimsooin77.sync.employee.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Sort;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "employeeNo", "employeeNo", "name", "name", "email", "companyEmail",
            "departmentCode", "departmentCode", "employmentStatus", "employmentStatus",
            "createdAt", "createdAt", "updatedAt", "updatedAt", "id", "id");
    private final EmployeeQueryService service;
    public EmployeeController(EmployeeQueryService service) { this.service = service; }

    @GetMapping
    public PageResponse<EmployeeResponse> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) EmploymentStatus employmentStatus,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false) String sortDirection) {
        var sort = PublicSortFactory.create(sortBy, sortDirection, "employeeNo", Sort.Direction.ASC, SORT_FIELDS);
        Page<EmployeeResponse> result = service.search(keyword, employmentStatus,
                        PageRequestFactory.create(page, size, sort))
                .map(EmployeeResponse::from);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public EmployeeResponse detail(@PathVariable Long id) {
        return service.find(id).map(EmployeeResponse::from).orElseThrow(() -> new EmployeeNotFoundException(id));
    }
}
