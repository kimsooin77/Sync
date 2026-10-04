package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeRepository;
import com.kimsooin77.sync.audit.AuditLogFactory;
import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.integration.IntegrationTaskFactory;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EmployeeSyncTransactionService {

    private final EmployeeRepository employeeRepository;
    private final SyncJobRepository syncJobRepository;
    private final SyncItemRepository syncItemRepository;
    private final EmployeeComparator employeeComparator;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogFactory auditLogFactory;
    private final IntegrationTaskRepository integrationTaskRepository;
    private final IntegrationTaskFactory integrationTaskFactory;

    public EmployeeSyncTransactionService(
            EmployeeRepository employeeRepository,
            SyncJobRepository syncJobRepository,
            SyncItemRepository syncItemRepository,
            EmployeeComparator employeeComparator,
            AuditLogRepository auditLogRepository,
            AuditLogFactory auditLogFactory,
            IntegrationTaskRepository integrationTaskRepository,
            IntegrationTaskFactory integrationTaskFactory
    ) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.syncJobRepository = Objects.requireNonNull(syncJobRepository, "syncJobRepository");
        this.syncItemRepository = Objects.requireNonNull(syncItemRepository, "syncItemRepository");
        this.employeeComparator = Objects.requireNonNull(employeeComparator, "employeeComparator");
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
        this.auditLogFactory = Objects.requireNonNull(auditLogFactory, "auditLogFactory");
        this.integrationTaskRepository = Objects.requireNonNull(
                integrationTaskRepository, "integrationTaskRepository");
        this.integrationTaskFactory = Objects.requireNonNull(
                integrationTaskFactory, "integrationTaskFactory");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncItemResult processEmployee(Long syncJobId, int rowNumber, NormalizedEmployee incoming) {
        Objects.requireNonNull(incoming, "incoming");
        SyncJob job = syncJobRepository.getReferenceById(syncJobId);

        Employee employee = employeeRepository.findByEmployeeNo(incoming.employeeNo()).orElse(null);
        if (employee == null) {
            Employee inserted = employeeRepository.saveAndFlush(new Employee(
                    incoming.employeeNo(), incoming.name(), incoming.email(), incoming.departmentCode(),
                    incoming.employmentStatus()));
            SyncItem item = syncItemRepository.saveAndFlush(SyncItem.inserted(job, rowNumber, inserted));
            auditLogRepository.saveAndFlush(auditLogFactory.created(inserted, item));
            integrationTaskFactory.forInserted(inserted, item)
                    .ifPresent(integrationTaskRepository::saveAndFlush);
            return SyncItemResult.INSERTED;
        }

        EmployeeChangeSet changeSet = employeeComparator.compare(employee, incoming);
        if (!changeSet.hasChanges()) {
            syncItemRepository.saveAndFlush(SyncItem.skipped(job, rowNumber, employee));
            return SyncItemResult.SKIPPED;
        }

        employee.updateSnapshot(
                incoming.name(), incoming.email(), incoming.departmentCode(), incoming.employmentStatus());
        employeeRepository.flush();
        SyncItem item = syncItemRepository.saveAndFlush(SyncItem.updated(job, rowNumber, employee));
        auditLogRepository.saveAndFlush(auditLogFactory.updated(employee, item, changeSet));
        integrationTaskRepository.saveAndFlush(integrationTaskFactory.forUpdated(employee, item, changeSet));
        return SyncItemResult.UPDATED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNormalizationFailure(Long syncJobId, NormalizationResult.Failure failure) {
        Objects.requireNonNull(failure, "failure");
        SyncJob job = syncJobRepository.getReferenceById(syncJobId);
        String employeeNo = normalizedOrRawEmployeeNo(failure.rawEmployeeNo());
        String errorCode = failure.errors().stream()
                .map(error -> error.code().name())
                .collect(Collectors.joining(","));
        String errorMessage = failure.errors().stream()
                .map(NormalizationError::message)
                .collect(Collectors.joining("; "));
        syncItemRepository.saveAndFlush(SyncItem.failed(
                job, failure.rowNumber(), employeeNo, errorCode, errorMessage));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDataIntegrityFailure(
            Long syncJobId,
            int rowNumber,
            String employeeNo
    ) {
        SyncJob job = syncJobRepository.getReferenceById(syncJobId);
        syncItemRepository.saveAndFlush(SyncItem.failed(
                job,
                rowNumber,
                employeeNo,
                "DATA_INTEGRITY_VIOLATION",
                "직원 또는 처리 결과가 데이터베이스 제약 조건을 만족하지 않습니다."));
    }

    private static String normalizedOrRawEmployeeNo(String rawEmployeeNo) {
        String normalized = rawEmployeeNo == null
                ? null
                : EmployeeNormalizer.normalizeEmployeeNo(rawEmployeeNo);
        return normalized == null ? rawEmployeeNo : normalized;
    }
}
