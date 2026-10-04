package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.Employee;
import com.kimsooin77.sync.employee.EmployeeRepository;
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

    public EmployeeSyncTransactionService(
            EmployeeRepository employeeRepository,
            SyncJobRepository syncJobRepository,
            SyncItemRepository syncItemRepository,
            EmployeeComparator employeeComparator
    ) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
        this.syncJobRepository = Objects.requireNonNull(syncJobRepository, "syncJobRepository");
        this.syncItemRepository = Objects.requireNonNull(syncItemRepository, "syncItemRepository");
        this.employeeComparator = Objects.requireNonNull(employeeComparator, "employeeComparator");
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
            syncItemRepository.saveAndFlush(SyncItem.inserted(job, rowNumber, inserted));
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
        syncItemRepository.saveAndFlush(SyncItem.updated(job, rowNumber, employee));
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
